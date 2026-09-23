package club.asbl.asbl_club.audit;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
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
class AuditJournalPageIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Test
    @WithMockUser(username = "alice@club.test")
    void admin_seesTheJournalWithTheCreationEntry() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");
        mockMvc.perform(post("/asbls").with(csrf())
                        .param("denomination", "Mon Club")
                        .param("bceNumber", "0123.456.789")
                        .param("slug", "mon-club")
                        .param("defaultLanguage", "fr"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/asbls/mon-club/audit"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ASBL_CREATED")));
    }

    @Test
    @WithMockUser(username = "bob@club.test")
    void nonAdmin_getsNotFound() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");
        userService.register("Bob", "bob@club.test", "password123");
        User alice = userService.getByEmail("alice@club.test");
        asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");

        mockMvc.perform(get("/asbls/mon-club/audit"))
                .andExpect(status().isNotFound());
    }
}
