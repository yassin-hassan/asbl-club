package club.asbl.asbl_club.account;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
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
class AccountExportIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Test
    @WithMockUser(username = "alice@club.test")
    void member_downloadsTheirPersonalData() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");
        mockMvc.perform(post("/asbls").with(csrf())
                        .param("denomination", "Mon Club")
                        .param("bceNumber", "0123.456.789")
                        .param("slug", "mon-club")
                        .param("defaultLanguage", "fr"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/account/export"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice@club.test")))
                .andExpect(content().string(containsString("Mon Club")));
    }
}
