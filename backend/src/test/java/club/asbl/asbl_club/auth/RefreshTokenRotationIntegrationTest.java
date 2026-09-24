package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RefreshTokenRotationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    User alice;

    @BeforeEach
    void registerAlice() {
        alice = userService.register("Alice", "alice@club.test", "password123");
    }

    @Test
    void validRefreshToken_givesANewAccessTokenAndANewRefreshToken() throws Exception {
        String loginToken = loginAndGetRefreshToken();

        MvcResult result = refresh(loginToken).andExpect(status().isOk()).andReturn();

        Jwt accessToken = jwtDecoder.decode(JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken"));
        assertThat(accessToken.getSubject()).isEqualTo(alice.getPublicId().toString());
        assertThat(accessToken.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(result.getResponse().getCookie("refresh_token").getValue()).isNotEqualTo(loginToken);
    }

    @Test
    void refreshToken_canOnlyBeUsedOnce() throws Exception {
        String loginToken = loginAndGetRefreshToken();
        refresh(loginToken).andExpect(status().isOk());

        refresh(loginToken).andExpect(status().isUnauthorized());
    }

    @Test
    void rotatedTokens_chainWithinOneFamily() throws Exception {
        String first = loginAndGetRefreshToken();
        String second = refreshAndGetRefreshToken(first);
        refreshAndGetRefreshToken(second);

        List<UUID> families = jdbcTemplate.queryForList(
                "SELECT family_id FROM refresh_tokens WHERE user_id = ?", UUID.class, alice.getId());
        assertThat(families).hasSize(3).containsOnly(families.get(0));
    }

    @Test
    void missingCookie_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownToken_isUnauthorizedAndTheCookieIsCleared() throws Exception {
        refresh("not-a-real-token")
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge("refresh_token", 0));
    }

    @Test
    void expiredToken_isUnauthorized() throws Exception {
        String loginToken = loginAndGetRefreshToken();
        jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE token_hash = ?",
                RefreshTokenService.hash(loginToken));
        // The SQL above bypassed Hibernate, which still holds the token from login in its cache.
        // Clear it so the refresh reads the row as it now is in the database.
        entityManager.clear();

        refresh(loginToken).andExpect(status().isUnauthorized());
    }

    @Test
    void closedAccount_cannotRefresh() throws Exception {
        String loginToken = loginAndGetRefreshToken();
        userService.anonymizeAndClose(alice);

        refresh(loginToken).andExpect(status().isUnauthorized());
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refresh_token", refreshToken)));
    }

    private String refreshAndGetRefreshToken(String refreshToken) throws Exception {
        return refresh(refreshToken).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token").getValue();
    }

    private String loginAndGetRefreshToken() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("refresh_token").getValue();
    }
}
