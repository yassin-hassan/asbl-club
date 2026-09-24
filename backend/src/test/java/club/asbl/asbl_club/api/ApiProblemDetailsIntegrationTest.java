package club.asbl.asbl_club.api;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

// Every kind of API error — from a controller, from validation, from the security layer — has the same
// Problem Details shape (RFC 9457), so the frontend handles errors one way.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiProblemDetailsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Test
    void validationError_listsTheInvalidFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\"}"))
                .andExpect(problem(400, "/api/v1/auth/login"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void notFound_fromAController() throws Exception {
        mockMvc.perform(get("/api/v1/events/999999")).andExpect(problem(404, "/api/v1/events/999999"));
    }

    @Test
    void wrongPassword_fromAController() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"wrong\"}"))
                .andExpect(problem(401, "/api/v1/auth/login"));
    }

    @Test
    void notAuthenticated_fromTheSecurityLayer_keepsTheBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(problem(401, "/api/v1/me"))
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void notAllowed_fromTheSecurityLayer() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");
        String token = accessToken("alice@club.test", "password123");

        mockMvc.perform(get("/api/v1/admin/anything").header("Authorization", "Bearer " + token))
                .andExpect(problem(403, "/api/v1/admin/anything"));
    }

    @Test
    void invalidRefreshToken_stillClearsTheCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", "not-a-real-token")))
                .andExpect(problem(401, "/api/v1/auth/refresh"))
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    private static ResultMatcher problem(int status, String path) {
        List<ResultMatcher> checks = List.of(
                status().is(status),
                content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON),
                jsonPath("$.status").value(status),
                jsonPath("$.title").exists(),
                jsonPath("$.instance").value(path));
        return result -> {
            for (ResultMatcher check : checks) {
                check.match(result);
            }
        };
    }

    private String accessToken(String email, String password) throws Exception {
        ResultActions login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
        return JsonPath.read(login.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }
}
