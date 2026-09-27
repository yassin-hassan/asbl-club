package club.asbl.asbl_club.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Every request has an ID: in the response header, on each log line written for it, and in the body of an
// unexpected error, so a user's "it failed" leads to the exact log lines.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class RequestIdIntegrationTest {

    static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    static final String FROM_THE_WORKER = "3f2b8c1e-7a4d-4e9b-9c2a-1d5e6f708192";

    @Autowired
    MockMvc mockMvc;

    @Test
    void aRequestWithoutAnId_getsOne_andItsLogLineCarriesIt(CapturedOutput output) throws Exception {
        String id = mockMvc.perform(get("/api/v1/asbls/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(RequestIdFilter.HEADER, org.hamcrest.Matchers.matchesPattern(UUID)))
                .andReturn().getResponse().getHeader(RequestIdFilter.HEADER);

        assertThat(output).containsPattern("\\[" + id + "\\] .* GET /api/v1/asbls/does-not-exist -> 404 in \\d+ ms");
    }

    @Test
    void theWorkersId_isKept() throws Exception {
        mockMvc.perform(get("/api/v1/asbls/does-not-exist").header(RequestIdFilter.HEADER, FROM_THE_WORKER))
                .andExpect(header().string(RequestIdFilter.HEADER, FROM_THE_WORKER));
    }

    // A client can't write into the logs: anything not shaped like a UUID (a fake second log line here) is replaced.
    @Test
    void anIdThatIsNotAUuid_isReplaced(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/asbls/does-not-exist").header(RequestIdFilter.HEADER, "x\nERROR fake line"))
                .andExpect(header().string(RequestIdFilter.HEADER, org.hamcrest.Matchers.matchesPattern(UUID)));

        assertThat(output).doesNotContain("fake line");
    }

    // A bug: the answer gives nothing away but the ID; the log has the stack trace under that same ID.
    @Test
    void anUnexpectedError_answersWithTheId_andIsLoggedUnderIt(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/v1/test-only/failing").with(jwt()).header(RequestIdFilter.HEADER, FROM_THE_WORKER))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error. Quote the request ID when reporting it."))
                .andExpect(jsonPath("$.requestId").value(FROM_THE_WORKER));

        assertThat(output).contains("[" + FROM_THE_WORKER + "] ")
                .containsPattern("ERROR .*\\[" + FROM_THE_WORKER + "\\] .*Unexpected error")
                .contains("IllegalStateException: database password in a stack trace")
                .containsPattern("GET /api/v1/test-only/failing -> 500");
    }

    @TestConfiguration
    static class FailingEndpoint {

        @RestController
        static class Failing {
            @GetMapping("/api/v1/test-only/failing")
            String fail() {
                throw new IllegalStateException("database password in a stack trace");
            }
        }
    }
}
