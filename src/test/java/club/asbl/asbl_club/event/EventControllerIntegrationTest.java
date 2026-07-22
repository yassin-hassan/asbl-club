package club.asbl.asbl_club.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
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
class EventControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    @Test
    @WithMockUser(username = "alice@club.test")
    void adminCreatesAnEvent() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        Asbl club = asblService.createAsbl(alice, "Club A", "0111.111.111", "club-a", "fr");

        mockMvc.perform(post("/asbls/club-a/events").with(csrf())
                        .param("title", "Concert")
                        .param("description", "Une soirée")
                        .param("startsAt", "2026-09-01T18:00")
                        .param("location", "Salle A")
                        .param("visibility", "PUBLIC"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/asbls/club-a/events"));

        assertThat(eventService.eventsOf(club)).hasSize(1);
    }

    @Test
    @WithMockUser(username = "bob@club.test")
    void nonMemberCannotOpenTheCreateForm() throws Exception {
        userService.register("Bob", "bob@club.test", "password123");
        User alice = userService.register("Alice", "alice@club.test", "password123");
        asblService.createAsbl(alice, "Club A", "0111.111.111", "club-a", "fr");

        mockMvc.perform(get("/asbls/club-a/events/new"))
                .andExpect(status().isNotFound());
    }
}
