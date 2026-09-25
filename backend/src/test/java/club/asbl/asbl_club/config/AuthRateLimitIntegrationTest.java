package club.asbl.asbl_club.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

// The limiter is off for the rest of the suite; this class switches it on. Counters are shared by the
// whole application context, so every test uses its own client IPs.
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "rate-limit.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthRateLimitIntegrationTest {

    private static final int LOGIN_LIMIT = 10;

    @Autowired
    MockMvc mockMvc;

    @Test
    void apiLogin_isRefusedWith429AfterTheLimit() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            apiLogin("10.0.1.1").andExpect(status().isUnauthorized());
        }

        apiLogin("10.0.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void theLimitIsPerClient() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            apiLogin("10.0.2.1").andExpect(status().isUnauthorized());
        }
        apiLogin("10.0.2.1").andExpect(status().isTooManyRequests());

        apiLogin("10.0.2.2").andExpect(status().isUnauthorized()); // someone else is unaffected
    }

    @Test
    void anEncodedPath_cannotSlipPastTheLimit() throws Exception {
        for (int i = 0; i < LOGIN_LIMIT; i++) {
            apiLogin("10.0.4.1").andExpect(status().isUnauthorized());
        }

        // "%6c" is "l": the application routes this to the login endpoint, so it must count too.
        mockMvc.perform(json(post(URI.create("/api/v1/auth/%6cogin"))).with(from("10.0.4.1")))
                .andExpect(status().isTooManyRequests());
    }

    private ResultActions apiLogin(String clientIp) throws Exception {
        return mockMvc.perform(json(post("/api/v1/auth/login")).with(from(clientIp)));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"nobody@club.test\", \"password\": \"wrong\"}");
    }

    private static RequestPostProcessor from(String clientIp) {
        return request -> {
            request.setRemoteAddr(clientIp);
            return request;
        };
    }
}
