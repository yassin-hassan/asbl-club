package club.asbl.asbl_club.rss;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RssFeedIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    private void seedClubWithEvents() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        var asbl = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        Event published = eventService.createEvent(asbl, "Concert Public", "A public concert",
                Instant.now().plusSeconds(3600), "Hall", "PUBLIC");
        published.setStatus("PUBLISHED");
        eventService.createEvent(asbl, "Members Meeting", "Reserved",
                Instant.now().plusSeconds(3600), "Room", "MEMBERS");
    }

    @Test
    void globalFeed_isPublicRssListingPublicEventsOnly() throws Exception {
        seedClubWithEvents();

        mockMvc.perform(get("/events/rss"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("application/rss+xml")))
                .andExpect(content().string(containsString("Concert Public")))
                .andExpect(content().string(not(containsString("Members Meeting"))));
    }

    @Test
    void asblFeed_listsThatAsblPublicEvents() throws Exception {
        seedClubWithEvents();

        mockMvc.perform(get("/asbls/mon-club/events/rss"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Concert Public")));
    }
}
