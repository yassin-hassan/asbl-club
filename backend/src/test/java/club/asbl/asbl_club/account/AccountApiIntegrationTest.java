package club.asbl.asbl_club.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.Map;
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

    @Autowired
    EntityManager entityManager;

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
    void myName_canBeChanged_andShowsAtOnce() throws Exception {
        mockMvc.perform(put("/api/v1/me/name").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"  Alice Martin  \"}"))
                .andExpect(status().isNoContent());

        // The same access token: the name comes from the database, not from the token.
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.name").value("Alice Martin"))
                .andExpect(jsonPath("$.email").value("alice@club.test"));
    }

    @Test
    void anEmptyOrTooLongName_isRefused() throws Exception {
        for (String name : new String[] {"", "   ", "x".repeat(256)}) {
            mockMvc.perform(put("/api/v1/me/name").header("Authorization", "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"" + name + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }
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

    // Right to be forgotten vs keeping accounting records 10 years: the account is anonymised and marked deleted
    // (soft delete), but each payment keeps its frozen copy of who paid, so the receipt stays valid (rule 24).
    @Test
    void delete_anonymisesTheAccount_butPaymentsKeepWhoPaid() throws Exception {
        var club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        Long payable = jdbcTemplate.queryForObject(
                "INSERT INTO payables (type, amount, currency) VALUES ('MEMBERSHIP', 25.00, 'EUR') RETURNING id",
                Long.class);
        jdbcTemplate.update("INSERT INTO payments (asbl_id, user_id, payer_name, payer_email, payable_id, amount, "
                + "commission, status, paid_at) VALUES (?, ?, 'Alice', 'alice@club.test', ?, 25.00, 1.05, "
                + "'SUCCEEDED', now())", club.getId(), alice.getId(), payable);

        mockMvc.perform(delete("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
        entityManager.flush(); // written by JPA in this test's transaction; the checks below read the tables

        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT name, email, deleted_at FROM users WHERE id = ?", alice.getId());
        assertThat(account.get("name")).isEqualTo("Deleted account");
        assertThat((String) account.get("email")).doesNotContain("alice");
        assertThat(account.get("deleted_at")).isNotNull(); // still there: payments point to it

        Map<String, Object> payment = jdbcTemplate.queryForMap(
                "SELECT payer_name, payer_email, amount, user_id FROM payments WHERE payable_id = ?", payable);
        assertThat(payment.get("payer_name")).isEqualTo("Alice");
        assertThat(payment.get("payer_email")).isEqualTo("alice@club.test");
        assertThat(payment.get("user_id")).isEqualTo(alice.getId());
    }

    @Test
    void everything_requiresLoggingIn() throws Exception {
        mockMvc.perform(get("/api/v1/me/associations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/export")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/me/name").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"X\"}"))
                .andExpect(status().isUnauthorized());
    }
}
