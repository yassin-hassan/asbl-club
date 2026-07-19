package club.asbl.asbl_club.asbl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.util.List;
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
class CreateAsblIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblRepository asblRepository;

    @Autowired
    MembershipRepository membershipRepository;

    @Test
    @WithMockUser(username = "alice@club.test")
    void loggedInUserCreatesAsbl_andBecomesAdmin() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        mockMvc.perform(post("/asbls").with(csrf())
                        .param("denomination", "Mon Club")
                        .param("bceNumber", "0123.456.789")
                        .param("slug", "mon-club")
                        .param("defaultLanguage", "fr"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        Asbl asbl = asblRepository.findBySlug("mon-club").orElseThrow();
        assertThat(asbl.getStatus()).isEqualTo("PENDING");

        User creator = userService.getByEmail("alice@club.test");
        List<Membership> memberships = membershipRepository.findByUser(creator);
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getRole()).isEqualTo("ADMIN");
        assertThat(memberships.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(memberships.get(0).getAsbl().getSlug()).isEqualTo("mon-club");
    }

    @Test
    @WithMockUser(username = "bob@club.test")
    void invalidBceNumber_isRejectedAndCreatesNothing() throws Exception {
        mockMvc.perform(post("/asbls").with(csrf())
                        .param("denomination", "Autre Club")
                        .param("bceNumber", "not-a-bce")
                        .param("slug", "autre-club")
                        .param("defaultLanguage", "fr"))
                .andExpect(status().isOk());

        assertThat(asblRepository.findBySlug("autre-club")).isEmpty();
    }
}
