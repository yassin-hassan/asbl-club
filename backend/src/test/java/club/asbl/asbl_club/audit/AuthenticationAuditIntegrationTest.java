package club.asbl.asbl_club.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthenticationAuditIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void successfulLogin_isAudited() throws Exception {
        String email = "auth-success@club.test";
        userService.register("Alice", email, "password123");

        mockMvc.perform(formLogin("/login").user(email).password("password123"))
                .andExpect(authenticated());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'LOGIN_SUCCEEDED' AND payload->>'email' = ?",
                Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void failedLogin_isAudited() throws Exception {
        String email = "auth-failure@club.test";
        userService.register("Alice", email, "password123");

        mockMvc.perform(formLogin("/login").user(email).password("wrong"))
                .andExpect(unauthenticated());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'LOGIN_FAILED' AND payload->>'email' = ?",
                Integer.class, email);
        assertThat(count).isEqualTo(1);
    }

    // The API login is a password login too: audited, linked to the user, with the caller's IP.
    @Test
    void apiLogin_isAudited() throws Exception {
        String email = "api-login@club.test";
        Long userId = userService.register("Alice", email, "password123").getId();

        apiLogin(email);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'LOGIN_SUCCEEDED' AND user_id = ? AND ip IS NOT NULL",
                Integer.class, userId);
        assertThat(count).isEqualTo(1);
    }

    // Regression: checking the access token on every API call fires the same "authentication succeeded" event
    // as a login. Recording those flooded the (append-only) journal with one fake login per request.
    @Test
    void apiCallsWithAToken_areNotLogins() throws Exception {
        String email = "api-calls@club.test";
        userService.register("Alice", email, "password123");
        String token = apiLogin(email);
        int loginsBefore = loginEntries(); // other test classes may have left login entries behind

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());

        assertThat(loginEntries()).isEqualTo(loginsBefore);
    }

    private int loginEntries() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action LIKE 'LOGIN_%'", Integer.class);
    }

    private String apiLogin(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
