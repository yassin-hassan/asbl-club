package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// Deliberately NOT @Transactional: every request commits, like in production. Reuse detection revokes
// the family and then throws; inside a test transaction a wrongly rolled-back revocation would still be
// visible, so only real commits prove it sticks. Hence unique emails instead of rollback for isolation,
// and tokens issued directly (no /login), so no audit rows are left behind for other tests.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RefreshTokenReuseDetectionIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    RefreshTokenService refreshTokenService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    User user;

    @BeforeEach
    void registerUser() {
        user = userService.register("Reuse", "reuse-" + UUID.randomUUID() + "@club.test", "password123");
    }

    @Test
    void reusingARotatedToken_revokesTheWholeFamily() throws Exception {
        String stolen = refreshTokenService.issue(user);
        String latest = refreshAndGetRefreshToken(stolen);   // real user refreshes: "stolen" is now rotated

        refresh(stolen).andExpect(status().isUnauthorized()); // attacker replays the old token

        refresh(latest).andExpect(status().isUnauthorized()); // ...and the real user's token is dead too
        Integer usable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, user.getId());
        assertThat(usable).isZero();
    }

    @Test
    void reuse_leavesAPermanentAuditTrace() throws Exception {
        String stolen = refreshTokenService.issue(user);
        refreshAndGetRefreshToken(stolen);

        refresh(stolen).andExpect(status().isUnauthorized());

        Integer traces = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'REFRESH_TOKEN_REUSE_DETECTED' AND user_id = ?",
                Integer.class, user.getId());
        assertThat(traces).isEqualTo(1);
    }

    @Test
    void reuseDetection_onlyEndsThatLoginSession() throws Exception {
        String laptop = refreshTokenService.issue(user);
        String phone = refreshTokenService.issue(user);
        refreshAndGetRefreshToken(laptop);

        refresh(laptop).andExpect(status().isUnauthorized()); // replay on the laptop family

        refresh(phone).andExpect(status().isOk());             // the phone's family is untouched
    }

    @Test
    void expiredToken_isRejectedButNotTreatedAsTheft() throws Exception {
        String token = refreshTokenService.issue(user);
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                RefreshTokenService.hash(token));

        refresh(token).andExpect(status().isUnauthorized());

        // Expiry is normal: the token is refused, but nothing was revoked.
        Integer revoked = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NOT NULL",
                Integer.class, user.getId());
        assertThat(revoked).isZero();
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)));
    }

    private String refreshAndGetRefreshToken(String refreshToken) throws Exception {
        return refresh(refreshToken).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token").getValue();
    }
}
