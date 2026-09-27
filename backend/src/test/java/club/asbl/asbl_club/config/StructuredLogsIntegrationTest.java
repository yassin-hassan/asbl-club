package club.asbl.asbl_club.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import club.asbl.asbl_club.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Production's log format (LOG_FORMAT=ecs): one JSON object per line, the request ID and the request's details as
// fields a log search can filter on.
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "logging.structured.format.console=ecs"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class StructuredLogsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void eachRequestIsOneJsonLine_withItsIdAndDetailsAsFields(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/asbls/does-not-exist")
                .header(RequestIdFilter.HEADER, RequestIdIntegrationTest.FROM_THE_WORKER));

        JsonNode line = output.getOut().lines()
                .filter(text -> text.contains("GET /api/v1/asbls/does-not-exist"))
                .map(text -> JsonMapper.shared().readTree(text))
                .findFirst().orElseThrow();
        assertThat(line.path("requestId").asString()).isEqualTo(RequestIdIntegrationTest.FROM_THE_WORKER);
        assertThat(line.path("status").asInt()).isEqualTo(404);
        assertThat(line.path("path").asString()).isEqualTo("/api/v1/asbls/does-not-exist");
        assertThat(line.path("log").path("level").asString()).isEqualTo("INFO");
    }
}
