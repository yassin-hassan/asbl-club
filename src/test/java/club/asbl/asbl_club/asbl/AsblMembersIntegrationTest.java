package club.asbl.asbl_club.asbl;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
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
class AsblMembersIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Test
    @WithMockUser(username = "alice@club.test")
    void memberSeesTheirOwnAssociationMembers() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        asblService.createAsbl(alice, "Club A", "0111.111.111", "club-a", "fr");

        mockMvc.perform(get("/asbls/club-a/members"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice@club.test")))
                .andExpect(content().string(containsString("ADMIN")));
    }

    @Test
    @WithMockUser(username = "alice@club.test")
    void nonMemberCannotSeeAnotherAssociationMembers() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        User bob = userService.register("Bob", "bob@club.test", "password123");
        asblService.createAsbl(alice, "Club A", "0111.111.111", "club-a", "fr");
        asblService.createAsbl(bob, "Club B", "0222.222.222", "club-b", "fr");

        mockMvc.perform(get("/asbls/club-b/members"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice@club.test")
    void unknownAssociationIsNotFound() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        mockMvc.perform(get("/asbls/does-not-exist/members"))
                .andExpect(status().isNotFound());
    }
}
