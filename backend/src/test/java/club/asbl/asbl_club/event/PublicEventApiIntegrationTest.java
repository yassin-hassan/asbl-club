package club.asbl.asbl_club.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// What anyone may read about an event without logging in: only published, public events.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PublicEventApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    Asbl club;

    @BeforeEach
    void anAssociation() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
    }

    // Drafts and members-only events are "not found" to the public: their existence isn't revealed either.
    @Test
    void onlyPublishedPublicEvents_areReadableByAnyone() throws Exception {
        Event published = event("Concert", "PUBLIC");
        eventService.publish(published);
        Event draft = event("Draft", "PUBLIC");
        Event membersOnly = event("Members only", "MEMBERS");
        eventService.publish(membersOnly);

        mockMvc.perform(get("/api/v1/events/" + published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Concert"));
        mockMvc.perform(get("/api/v1/events/" + draft.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/" + membersOnly.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/events/" + draft.getId() + "/availability")).andExpect(status().isNotFound());
    }

    @Test
    void availability_givesTheRemainingSeatsPerCategory() throws Exception {
        Event published = event("Concert", "PUBLIC");
        eventService.addTicketCategory(published, "Standard", new BigDecimal("10.00"), 100);
        eventService.publish(published);

        mockMvc.perform(get("/api/v1/events/" + published.getId() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remaining").value(100));
    }

    private Event event(String title, String visibility) {
        return eventService.createEvent(club, title, null, Instant.now().plusSeconds(3600), "Hall", visibility);
    }
}
