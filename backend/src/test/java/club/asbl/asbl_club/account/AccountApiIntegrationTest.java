package club.asbl.asbl_club.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    User alice;
    String accessToken;
    String refreshToken;

    @BeforeEach
    void aliceLogsIn() throws Exception {
        alice = userService.register("Alice", "alice@club.test", "password123");
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}"))
                .andReturn();
        accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        refreshToken = login.getResponse().getCookie("refresh_token").getValue();
    }

    @Test
    void myAssociations_listsWhereIAmAMemberAndMyRole() throws Exception {
        asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");

        mockMvc.perform(get("/api/v1/me/associations").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("mon-club"))
                .andExpect(jsonPath("$[0].denomination").value("Mon Club"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));
    }

    @Test
    void export_containsMyProfile() throws Exception {
        mockMvc.perform(get("/api/v1/me/export").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("alice@club.test"));
    }

    @Test
    void delete_anonymisesTheAccountAuditsItAndEndsEverySession() throws Exception {
        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        assertThat(userService.findByEmail("alice@club.test")).isEmpty(); // email anonymised

        // Audited with the account as actor: the audit log resolves an API (token) login too.
        Integer audited = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'ACCOUNT_DELETED' AND user_id = ?",
                Integer.class, alice.getId());
        assertThat(audited).isEqualTo(1);

        // Every session revoked, immediately.
        Integer usable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, alice.getId());
        assertThat(usable).isZero();
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)))
                .andExpect(status().isUnauthorized());

        // And the old password no longer opens anything: the account is closed, not just logged out.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void everything_requiresLoggingIn() throws Exception {
        mockMvc.perform(get("/api/v1/me/associations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/me")).andExpect(status().isUnauthorized());
    }
}
