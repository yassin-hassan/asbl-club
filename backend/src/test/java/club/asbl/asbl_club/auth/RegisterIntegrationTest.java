package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RegisterIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void newAccount_isCreatedAndLoggedInStraightAway() throws Exception {
        String body = register("Alice", "Alice@Club.TEST", "password123")
                .andExpect(status().isCreated())
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn().getResponse().getContentAsString();

        String email = jwtDecoder.decode(JsonPath.read(body, "$.accessToken")).getClaimAsString("email");
        assertThat(email).isEqualTo("alice@club.test"); // emails are case-insensitive: stored lower-case
        assertThat(userService.findByEmail("alice@club.test")).isPresent();
    }

    @Test
    void newAccount_loginIsAuditedLikeAnyOther() throws Exception {
        register("Alice", "alice@club.test", "password123").andExpect(status().isCreated());

        Integer audited = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'LOGIN_SUCCEEDED' AND payload->>'email' = ?",
                Integer.class, "alice@club.test");
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void takenEmail_isAConflictInTheRequestedLanguage() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        register("Other Alice", "alice@club.test", "password456", "en")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").value("This email address is already in use"))
                .andExpect(cookie().doesNotExist("refresh_token"));
    }

    @Test
    void tooShortPassword_isRejectedWithTheFieldNamed() throws Exception {
        register("Alice", "alice@club.test", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
        assertThat(userService.findByEmail("alice@club.test")).isEmpty();
    }

    @Test
    void invalidEmail_isRejectedWithTheFieldNamed() throws Exception {
        register("Alice", "not-an-email", "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    private ResultActions register(String name, String email, String password) throws Exception {
        return register(name, email, password, "fr");
    }

    private ResultActions register(String name, String email, String password, String language) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register")
                .header("Accept-Language", language)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}".formatted(name, email, password)));
    }
}
