package club.asbl.asbl_club.event;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EventManagementIntegrationTest {

    private static final String BASE = "/api/v1/asbls/mon-club/manage/events";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EventService eventService;

    @Autowired
    EntityManager entityManager;

    String adminToken;
    String memberToken;
    String outsiderToken;

    @BeforeEach
    void anAssociationWithAnAdminAMemberAndAnOutsider() throws Exception {
        User admin = userService.register("Admin", "admin@club.test", "password123");
        Asbl club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        User member = userService.register("Member", "member@club.test", "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                member.getId(), club.getId());
        userService.register("Outsider", "outsider@club.test", "password123");
        adminToken = tokenFor("admin@club.test");
        memberToken = tokenFor("member@club.test");
        outsiderToken = tokenFor("outsider@club.test");
    }

    @Test
    void anAdministrator_createsADraft_addsTickets_andPublishesIt() throws Exception {
        String body = createEvent(adminToken)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(BASE + "/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.startsAt").value("2030-12-01T19:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        // A draft isn't public yet.
        mockMvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());

        String withTicket = send(post(BASE + "/" + id + "/tickets"), adminToken,
                "{\"label\": \"Standard\", \"price\": 12.50, \"totalSeats\": 100}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tickets[0].label").value("Standard"))
                .andExpect(jsonPath("$.tickets[0].soldSeats").value(0))
                .andExpect(jsonPath("$.tickets[0].pendingSeats").value(0))
                .andReturn().getResponse().getContentAsString();
        Integer ticket = JsonPath.read(withTicket, "$.tickets[0].id");

        send(post(BASE + "/" + id + "/publish"), adminToken, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk());

        // A booking holds its seat while it's being paid: taken, but not paid yet.
        send(post(BASE + "/" + id + "/registrations"), memberToken, "{\"ticketCategoryId\": " + ticket + "}")
                .andExpect(status().isCreated());
        entityManager.clear(); // the seat count is updated in SQL: read it afresh, as the next request would
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.tickets[0].soldSeats").value(1))
                .andExpect(jsonPath("$.tickets[0].pendingSeats").value(1));
        // A plain member sees what's left, not how the sales stand.
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", "Bearer " + memberToken))
                .andExpect(jsonPath("$.tickets[0].soldSeats").value(1))
                .andExpect(jsonPath("$.tickets[0].pendingSeats").doesNotExist());
        jdbcTemplate.update("UPDATE registrations SET status = 'PAID' WHERE ticket_category_id = ?", ticket);
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.tickets[0].soldSeats").value(1))
                .andExpect(jsonPath("$.tickets[0].pendingSeats").value(0));
    }

    // A plain member sees what they can go to: published events still to come. Drafts (the administrators' work in
    // progress), past and cancelled events are "not found" for them, in the list and by their address.
    @Test
    void aPlainMember_seesOnlyPublishedEventsStillToCome() throws Exception {
        Integer upcoming = publishedEvent("Concert", "2030-12-01T19:00:00Z");
        Integer draft = JsonPath.read(createEvent(adminToken).andReturn().getResponse().getContentAsString(), "$.id");
        Integer past = publishedEvent("Last year's party", "2030-11-01T19:00:00Z");
        jdbcTemplate.update("UPDATE events SET starts_at = now() - interval '30 days' WHERE id = ?", past);
        Integer cancelled = publishedEvent("Cancelled fair", "2030-10-01T19:00:00Z");
        send(post(BASE + "/" + cancelled + "/cancel"), adminToken, "").andExpect(status().isOk());

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.events[*].id").value(org.hamcrest.Matchers.contains(upcoming)));
        mockMvc.perform(get(BASE + "/" + upcoming).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSeeSales").value(false));
        for (Integer hidden : new Integer[] {draft, past, cancelled}) {
            mockMvc.perform(get(BASE + "/" + hidden).header("Authorization", "Bearer " + memberToken))
                    .andExpect(status().isNotFound());
        }

        createEvent(memberToken).andExpect(status().isForbidden());
        send(post(BASE + "/" + draft + "/publish"), memberToken, "").andExpect(status().isForbidden());
    }

    // A reader follows the activity like an administrator (every event, how the sales stand), without changing
    // anything, and without the attendee list (personal data).
    @Test
    void aReader_seesEveryEvent_andHowTheSalesStand_butChangesNothing() throws Exception {
        Integer draft = JsonPath.read(createEvent(adminToken).andReturn().getResponse().getContentAsString(), "$.id");
        Integer past = publishedEvent("Last year's party", "2030-11-01T19:00:00Z");
        jdbcTemplate.update("UPDATE events SET starts_at = now() - interval '30 days' WHERE id = ?", past);
        jdbcTemplate.update("UPDATE memberships SET role = 'VIEWER' WHERE user_id = "
                + "(SELECT id FROM users WHERE email = 'member@club.test')");

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + memberToken))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.canManage").value(false));
        mockMvc.perform(get(BASE + "/" + past).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canSeeSales").value(true))
                .andExpect(jsonPath("$.canSeeAttendees").value(false))
                .andExpect(jsonPath("$.tickets[0].pendingSeats").value(0));
        mockMvc.perform(get(BASE + "/" + draft).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());
        send(post(BASE + "/" + draft + "/publish"), memberToken, "").andExpect(status().isForbidden());
    }

    @Test
    void anOutsider_isRefusedEverywhere_andAnonymousNeedsToLogIn() throws Exception {
        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + outsiderToken)).andExpect(status().isForbidden());
        createEvent(outsiderToken).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // IDOR: an admin of one association must not reach another association's event by putting its ID under
    // their own association's URL.
    @Test
    void anEventOfAnotherAssociation_isNotFoundUnderThisOne() throws Exception {
        User other = userService.register("Other", "other@club.test", "password123");
        Asbl otherClub = asblService.createAsbl(other, "Other Club", "0987.654.321", "other-club", "fr");
        Event foreign = eventServiceCreate(otherClub);

        mockMvc.perform(get(BASE + "/" + foreign.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        send(post(BASE + "/" + foreign.getId() + "/publish"), adminToken, "").andExpect(status().isNotFound());
    }

    @Test
    void invalidEvents_areRejectedPerField() throws Exception {
        send(post(BASE), adminToken, "{\"title\": \"\", \"visibility\": \"EVERYONE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists())
                .andExpect(jsonPath("$.errors.visibility").exists());
    }

    private Event eventServiceCreate(Asbl asbl) {
        return eventService.createEvent(asbl, "Foreign", null, Instant.parse("2030-12-01T19:00:00Z"),
                null, "PUBLIC");
    }

    // A published event with one ticket category, by the administrator; its id.
    private Integer publishedEvent(String title, String startsAt) throws Exception {
        Integer id = JsonPath.read(send(post(BASE), adminToken, """
                {"title": "%s", "startsAt": "%s", "visibility": "PUBLIC"}
                """.formatted(title, startsAt)).andReturn().getResponse().getContentAsString(), "$.id");
        send(post(BASE + "/" + id + "/tickets"), adminToken,
                "{\"label\": \"Standard\", \"price\": 10, \"totalSeats\": 10}").andExpect(status().isCreated());
        send(post(BASE + "/" + id + "/publish"), adminToken, "").andExpect(status().isOk());
        return id;
    }

    private ResultActions createEvent(String token) throws Exception {
        return send(post(BASE), token, """
                {"title": "Concert", "description": "A concert", "startsAt": "2030-12-01T19:00:00Z",
                 "location": "Hall", "visibility": "PUBLIC"}
                """);
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
            String token, String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
