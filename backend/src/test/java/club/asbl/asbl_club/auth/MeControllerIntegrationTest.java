package club.asbl.asbl_club.auth;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class MeControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    User alice;

    @BeforeEach
    void registerAlice() {
        alice = userService.register("Alice", "alice@club.test", "password123");
    }

    @Test
    void withoutToken_isUnauthorizedAndSaysBearerIsExpected() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void withTokenFromLogin_returnsTheUser() throws Exception {
        String token = accessTokenFor("alice@club.test", "password123");

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alice.getPublicId().toString()))
                .andExpect(jsonPath("$.email").value("alice@club.test"))
                .andExpect(jsonPath("$.roles").value(contains("USER")));
    }

    @Test
    void withTamperedToken_isUnauthorized() throws Exception {
        String token = accessTokenFor("alice@club.test", "password123");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void withGarbageToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    private String accessTokenFor(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
