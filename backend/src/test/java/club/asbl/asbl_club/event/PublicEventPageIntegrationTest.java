package club.asbl.asbl_club.event;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
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
        published.setStatus(EventStatus.PUBLISHED);
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

    @Test
    void publicEventPage_carriesCspHeaderWithNonceMatchingInlineScript() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        Asbl asbl = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        Event published = eventService.createEvent(asbl, "Concert", "A public concert",
                Instant.now().plusSeconds(3600), "Hall", "PUBLIC");
        published.setStatus(EventStatus.PUBLISHED);

        MvcResult result = mockMvc.perform(get("/events/" + published.getId()))
                .andExpect(status().isOk())
                .andReturn();

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertNotNull(csp, "response should carry a Content-Security-Policy header");
        Matcher matcher = Pattern.compile("'nonce-([A-Za-z0-9_-]+)'").matcher(csp);
        assertTrue(matcher.find(), "CSP header should carry a script nonce");
        String nonce = matcher.group(1);

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("nonce=\"" + nonce + "\""),
                "the inline script should carry the same nonce as the CSP header");
    }

    @Test
    void availabilityEndpoint_returnsRemainingSeatsAsJson() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        Asbl asbl = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        Event published = eventService.createEvent(asbl, "Concert", "A public concert",
                Instant.now().plusSeconds(3600), "Hall", "PUBLIC");
        published.setStatus(EventStatus.PUBLISHED);
        eventService.addTicketCategory(published, "Standard", new BigDecimal("10.00"), 100);

        mockMvc.perform(get("/events/" + published.getId() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remaining").value(100));
    }
}
