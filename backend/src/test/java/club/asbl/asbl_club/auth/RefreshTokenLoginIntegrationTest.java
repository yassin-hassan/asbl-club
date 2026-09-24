package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RefreshTokenLoginIntegrationTest {

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
    void login_setsAHardenedRefreshTokenCookie() throws Exception {
        mockMvc.perform(loginRequest())
                .andExpect(status().isOk())
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().sameSite("refresh_token", "Strict"))
                .andExpect(cookie().path("refresh_token", "/api/v1/auth"))
                .andExpect(cookie().maxAge("refresh_token", (int) Duration.ofDays(14).toSeconds()));
    }

    @Test
    void login_neverPutsTheRefreshTokenInTheBody() throws Exception {
        MvcResult result = mockMvc.perform(loginRequest())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String rawToken = result.getResponse().getCookie("refresh_token").getValue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawToken);
    }

    @Test
    void database_storesOnlyTheHashOfTheRefreshToken() throws Exception {
        Cookie cookie = mockMvc.perform(loginRequest()).andReturn().getResponse().getCookie("refresh_token");

        List<String> storedHashes = jdbcTemplate.queryForList(
                "SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, alice.getId());

        assertThat(storedHashes).containsExactly(RefreshTokenService.hash(cookie.getValue()));
        assertThat(storedHashes).doesNotContain(cookie.getValue());
    }

    @Test
    void eachLogin_startsItsOwnTokenFamily() throws Exception {
        mockMvc.perform(loginRequest()).andExpect(status().isOk());
        mockMvc.perform(loginRequest()).andExpect(status().isOk());

        List<UUID> families = jdbcTemplate.queryForList(
                "SELECT family_id FROM refresh_tokens WHERE user_id = ?", UUID.class, alice.getId());

        assertThat(families).hasSize(2).doesNotHaveDuplicates();
    }

    @Test
    void failedLogin_issuesNoRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("refresh_token"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, alice.getId());
        assertThat(rows).isZero();
    }

    private RequestBuilder loginRequest() {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}");
    }
}
