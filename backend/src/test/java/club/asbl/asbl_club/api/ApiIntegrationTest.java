package club.asbl.asbl_club.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.EventStatus;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
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
class ApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    private Event seedPublishedEvent() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        Asbl asbl = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        Event published = eventService.createEvent(asbl, "Concert", "A public concert",
                Instant.now().plusSeconds(3600), "Hall", "PUBLIC");
        published.setStatus(EventStatus.PUBLISHED);
        return published;
    }

    @Test
    void publicEvents_areReadableWithoutToken() throws Exception {
        seedPublishedEvent();

        mockMvc.perform(get("/api/v1/asbls/mon-club/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Concert"));
    }

    @Test
    void eventDetail_isReadableWithoutToken() throws Exception {
        Event published = seedPublishedEvent();

        mockMvc.perform(get("/api/v1/events/" + published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Concert"));
    }

    @Test
    void eventDetail_includesAssociationLocationAndTickets() throws Exception {
        Event published = seedPublishedEvent();
        eventService.addTicketCategory(published, "Standard", new BigDecimal("12.50"), 100);

        mockMvc.perform(get("/api/v1/events/" + published.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Hall"))
                .andExpect(jsonPath("$.asbl.slug").value("mon-club"))
                .andExpect(jsonPath("$.asbl.denomination").value("Mon Club"))
                .andExpect(jsonPath("$.tickets[0].label").value("Standard"))
                .andExpect(jsonPath("$.tickets[0].price").value(12.50))
                .andExpect(jsonPath("$.tickets[0].remaining").value(100));
    }

    @Test
    void asblDetail_isReadableWithoutToken() throws Exception {
        seedPublishedEvent();

        mockMvc.perform(get("/api/v1/asbls/mon-club"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").value("Mon Club"));
    }

    @Test
    void crossOriginRead_carriesCorsHeader() throws Exception {
        seedPublishedEvent();

        mockMvc.perform(get("/api/v1/asbls/mon-club/events").header("Origin", "https://tennis-wavre.be"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    @Test
    void write_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/asbls/mon-club/events"))
                .andExpect(status().isUnauthorized());
    }

    // Production doesn't advertise its endpoints: the API description is only served when switched on
    // (API_DOCS_ENABLED=true); its content is checked by OpenApiContractTest.
    @Test
    void apiDescriptionAndSwaggerUi_areOffByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
