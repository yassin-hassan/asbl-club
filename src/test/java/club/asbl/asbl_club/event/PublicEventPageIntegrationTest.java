package club.asbl.asbl_club.event;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PublicEventPageIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    @Test
    void publicPublishedEvent_isReachableWithoutLogin_andHidesOthers() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        Asbl asbl = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        Event published = eventService.createEvent(asbl, "Concert", "A public concert",
                Instant.now().plusSeconds(3600), "Hall", "PUBLIC");
        published.setStatus("PUBLISHED");
        Event members = eventService.createEvent(asbl, "Members only", "Reserved",
                Instant.now().plusSeconds(3600), "Room", "MEMBERS");

        mockMvc.perform(get("/events/" + published.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Concert")))
                .andExpect(content().string(containsString("og:title")))
                .andExpect(content().string(containsString("og:url")));

        mockMvc.perform(get("/events/" + members.getId()))
                .andExpect(status().isNotFound());
    }
}
