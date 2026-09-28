package club.asbl.asbl_club.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Error tracking: a bug reaches Sentry as one event, tagged with the request ID, without the visitor's credentials;
// ordinary answers (a 404) send nothing. Sentry is played by a local HTTP server receiving what the SDK sends.
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "sentry.environment=test"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@DirtiesContext // Sentry is set up once per JVM: closed with this context, so other tests don't report to it
class SentryIntegrationTest {

    static final BlockingQueue<JsonNode> EVENTS = new LinkedBlockingQueue<>();
    static final HttpServer SENTRY = fakeSentry();

    @DynamicPropertySource
    static void reportToTheFakeSentry(DynamicPropertyRegistry registry) {
        registry.add("sentry.dsn", () -> "http://public-key@localhost:" + SENTRY.getAddress().getPort() + "/1");
    }

    @AfterAll
    static void stopTheFakeSentry() {
        SENTRY.stop(0);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void aBug_isReportedOnce_taggedWithTheRequestId_withoutTheVisitorsCookies() throws Exception {
        mockMvc.perform(get("/api/v1/asbls/does-not-exist")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/test-only/failing").with(jwt())
                        .header(RequestIdFilter.HEADER, RequestIdIntegrationTest.FROM_THE_WORKER)
                        .cookie(new Cookie("refresh_token", "secret-refresh-token")))
                .andExpect(status().isInternalServerError());

        JsonNode event = EVENTS.poll(10, TimeUnit.SECONDS);
        assertThat(event).as("an event reached Sentry").isNotNull();
        assertThat(event.path("level").asString()).isEqualTo("error");
        assertThat(event.path("environment").asString()).isEqualTo("test");
        assertThat(event.path("tags").path("requestId").asString()).isEqualTo(RequestIdIntegrationTest.FROM_THE_WORKER);
        assertThat(event.path("exception").toString()).contains("IllegalStateException", "a bug in a controller");
        assertThat(event.toString()).doesNotContain("secret-refresh-token");

        // The 404 before it, and the same bug reported twice (by the log and by Spring): neither happens.
        assertThat(EVENTS.poll(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)).isNull();
    }

    @TestConfiguration
    static class FailingEndpoint {

        @RestController
        static class Failing {
            @GetMapping("/api/v1/test-only/failing")
            String fail() {
                throw new IllegalStateException("a bug in a controller");
            }
        }
    }

    // Sentry's ingestion endpoint (POST /api/<project>/envelope/): an envelope is lines of JSON, a header then
    // items (each a header line and a payload line); errors are the items of type "event".
    private static HttpServer fakeSentry() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                boolean gzipped = "gzip".equalsIgnoreCase(exchange.getRequestHeaders().getFirst("Content-Encoding"));
                List<String> lines = unzipped(body, gzipped).lines().toList();
                for (int i = 1; i + 1 < lines.size(); i += 2) {
                    if ("event".equals(JsonMapper.shared().readTree(lines.get(i)).path("type").asString())) {
                        EVENTS.add(JsonMapper.shared().readTree(lines.get(i + 1)));
                    }
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String unzipped(byte[] body, boolean gzipped) throws IOException {
        try (InputStream in = gzipped ? new GZIPInputStream(new ByteArrayInputStream(body))
                : new ByteArrayInputStream(body)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
