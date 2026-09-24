package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class LogoutIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    User alice;

    @BeforeEach
    void registerAlice() {
        alice = userService.register("Alice", "alice@club.test", "password123");
    }

    @Test
    void logout_clearsTheCookieAndTheRefreshTokenStopsWorking() throws Exception {
        String refreshToken = loginAndGetRefreshToken();

        logout(refreshToken)
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void logout_revokesEveryTokenOfThatLoginSession() throws Exception {
        String first = loginAndGetRefreshToken();
        String latest = refresh(first).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token").getValue();

        logout(latest).andExpect(status().isNoContent());

        Integer usable = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, alice.getId());
        assertThat(usable).isZero();
    }

    @Test
    void logout_leavesOtherLoginSessionsAlone() throws Exception {
        String laptop = loginAndGetRefreshToken();
        String phone = loginAndGetRefreshToken();

        logout(laptop).andExpect(status().isNoContent());

        refresh(phone).andExpect(status().isOk());
    }

    @Test
    void logout_withoutCookie_stillSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void logout_withUnknownToken_stillSucceeds() throws Exception {
        logout("not-a-real-token").andExpect(status().isNoContent());
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", refreshToken)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)));
    }

    private String loginAndGetRefreshToken() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token").getValue();
    }
}
