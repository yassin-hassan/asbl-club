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

    // A real server (not MockMvc), so the whole error path runs. There are no pages any more: an unknown address
    // outside the API is refused (deny by default), and the answer reveals nothing about the application.
    @Test
    void unknownAddress_isRefusedWithoutRevealingInternals() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/images/does-not-exist.png"))
                .header("Accept", "text/html")
                .GET()
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).doesNotContain("Exception");
        assertThat(response.body()).doesNotContain("club.asbl");
    }
}
