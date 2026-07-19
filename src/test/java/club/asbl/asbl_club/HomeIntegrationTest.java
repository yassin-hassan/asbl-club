package club.asbl.asbl_club;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class HomeIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Test
    @WithMockUser(username = "alice@club.test")
    void homeListsUsersAsblsWithRole() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mon Club")))
                .andExpect(content().string(containsString("ADMIN")));
    }

    @Test
    @WithMockUser(username = "bob@club.test")
    void homeShowsEmptyMessageWhenUserHasNoAsbls() throws Exception {
        userService.register("Bob", "bob@club.test", "password123");

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("aucune")));
    }
}
