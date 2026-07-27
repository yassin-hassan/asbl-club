package club.asbl.asbl_club.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AccountDeletionIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AccountService accountService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "alice@club.test")
    void deletingAccount_closesAndAnonymizesTheUser() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        mockMvc.perform(post("/account/delete").with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(userService.findByEmail("alice@club.test")).isEmpty();
        Integer closed = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE deleted_at IS NOT NULL", Integer.class);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    void deletedUser_cannotLogInWithOldCredentials() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        accountService.deleteAccount(alice);

        mockMvc.perform(formLogin("/login").user("alice@club.test").password("password123"))
                .andExpect(unauthenticated());
    }
}
