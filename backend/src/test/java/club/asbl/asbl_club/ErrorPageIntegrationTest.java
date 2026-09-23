package club.asbl.asbl_club;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
class ErrorPageIntegrationTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void unknownPageShowsFriendlyErrorWithoutStackTrace() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/images/does-not-exist.png"))
                .header("Accept", "text/html")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Page introuvable");
        assertThat(response.body()).doesNotContain("Whitelabel");
        assertThat(response.body()).doesNotContain("Exception");
    }
}
