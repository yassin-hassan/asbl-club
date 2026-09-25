package club.asbl.asbl_club.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.UserService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// The site moved to the CDN's address: pages opened on the server's own address are sent there; machine
// endpoints and the CDN's own relayed requests are not.
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "edge.secret=test-edge-secret",
        "edge.public-url=https://asbl-club.example.workers.dev"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RedirectToSiteIntegrationTest {

    private static final String SITE = "https://asbl-club.example.workers.dev";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    // Old links and bookmarks land on the same page of the new site, query included.
    @Test
    void pagesOnTheServersOwnAddress_goToTheSamePageOnTheSite() throws Exception {
        mockMvc.perform(get("/asbls/mon-club/members").queryParam("tab", "roles"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", SITE + "/asbls/mon-club/members?tab=roles"));
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", SITE + "/"));
        mockMvc.perform(head("/events/42"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", SITE + "/events/42"));
    }

    @Test
    void machineEndpoints_stayOnTheServer() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/asbls/nobody")).andExpect(status().isNotFound());
        // Stripe posts payment results here; without a valid signature it's refused, but not redirected.
        mockMvc.perform(post("/webhooks/stripe").content("{}"))
                .andExpect(header().doesNotExist("Location"));
    }

    // Form posts aren't page visits: a redirect would turn them into GETs and lose the data.
    @Test
    void formPosts_areNotRedirected() throws Exception {
        mockMvc.perform(post("/login").param("username", "x").param("password", "y"))
                .andExpect(header().string("Location", not(containsString(SITE))));
    }

    // The CDN relays some paths to us (here an RSS feed); they already come "from" the site and must be served,
    // or the CDN's request would be sent back to the CDN, round in circles.
    @Test
    void requestsRelayedByTheCdn_areServedHere() throws Exception {
        var admin = userService.register("Admin", "admin@club.test", "password123");
        var club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        Event gala = eventService.createEvent(club, "Gala", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.publish(gala);

        mockMvc.perform(get("/asbls/mon-club/events/rss")
                        .header(EdgeProxyFilter.SECRET_HEADER, "test-edge-secret")
                        .header(EdgeProxyFilter.CLIENT_IP_HEADER, "203.0.113.42"))
                .andExpect(status().isOk())
                // The feed's links point at the site, where people read the event.
                .andExpect(content().string(containsString(SITE + "/events/" + gala.getId())));
    }
}
