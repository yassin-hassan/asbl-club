package club.asbl.asbl_club.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
}
