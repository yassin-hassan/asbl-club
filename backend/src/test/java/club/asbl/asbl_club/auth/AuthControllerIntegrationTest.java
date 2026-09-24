package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void validCredentials_returnAnAccessTokenForThatUser() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");

        String body = login("alice@club.test", "password123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(alice.getPublicId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("alice@club.test");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("asbl-club");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
    }

    @Test
    void superAdminToken_carriesTheSuperAdminRole() throws Exception {
        userService.registerSuperAdmin("Root", "root@club.test", "password123");

        Jwt jwt = jwtDecoder.decode(accessToken("root@club.test", "password123"));

        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyInAnyOrder("USER", "SUPERADMIN");
    }

    // Why "sub" is the public ID and not the email: a closed account frees its email for someone else.
    @Test
    void reusedEmail_belongsToADifferentSubject() throws Exception {
        User original = userService.register("Alice", "alice@club.test", "password123");
        userService.anonymizeAndClose(original);
        userService.register("New Alice", "alice@club.test", "password456");

        Jwt jwt = jwtDecoder.decode(accessToken("alice@club.test", "password456"));

        assertThat(jwt.getSubject()).isNotEqualTo(original.getPublicId().toString());
    }

    @Test
    void wrongPassword_isRejected() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        login("alice@club.test", "wrong-password").andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEmail_isRejectedTheSameWayAsAWrongPassword() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        String wrongPassword = login("alice@club.test", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@club.test", "password123")
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void missingPassword_isABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiLogin_isAuditedLikeFormLogin() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        login("alice@club.test", "password123").andExpect(status().isOk());
        login("alice@club.test", "wrong-password").andExpect(status().isUnauthorized());

        assertThat(auditCount("LOGIN_SUCCEEDED", "alice@club.test")).isEqualTo(1);
        assertThat(auditCount("LOGIN_FAILED", "alice@club.test")).isEqualTo(1);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private String accessToken(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private Integer auditCount(String action, String email) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = ? AND payload->>'email' = ?",
                Integer.class, action, email);
    }
}
