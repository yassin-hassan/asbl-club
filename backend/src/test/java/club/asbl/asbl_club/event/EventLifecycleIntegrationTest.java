package club.asbl.asbl_club.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.payment.Registration;
import club.asbl.asbl_club.payment.ReservationService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

// An event's life after creation: edit it, change its tickets, cancel it once published, delete it while a draft.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EventLifecycleIntegrationTest {

    private static final String BASE = "/api/v1/asbls/mon-club/manage/events";
    private static final String EDIT = """
            {"title": "Concert (new date)", "description": "Moved", "startsAt": "2026-12-08T19:00:00Z",
             "location": "Big hall", "visibility": "PUBLIC"}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    @Autowired
    ReservationService reservationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    Asbl club;
    User member;
    Event concert;
    Long standard;
    String adminToken;
    String memberToken;

    @BeforeEach
    void aPublishedConcertWithOneTicketCategory() throws Exception {
        User admin = userService.register("Admin", "admin@club.test", "password123");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        member = userService.register("Member", "member@club.test", "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                member.getId(), club.getId());
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 10);
        standard = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
        adminToken = tokenFor("admin@club.test");
        memberToken = tokenFor("member@club.test");
    }

    @Test
    void anAdmin_editsAPublishedEvent_andOnlyTheChangesAreAudited() throws Exception {
        send(put(BASE + "/" + concert.getId()), adminToken, EDIT)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Concert (new date)"))
                .andExpect(jsonPath("$.startsAt").value("2026-12-08T19:00:00Z"));

        mockMvc.perform(get("/api/v1/events/" + concert.getId()))
                .andExpect(jsonPath("$.title").value("Concert (new date)"));
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM audit_logs WHERE action = 'EVENT_UPDATED' AND asbl_id = ?", String.class,
                club.getId());
        assertThat(payload).contains("title", "startsAt", "location").doesNotContain("visibility");
    }

    @Test
    void seats_cannotGoBelowThoseAlreadyTaken() throws Exception {
        book();
        book();

        editTicket(standard, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEATS_BELOW_SOLD"));
        editTicket(standard, 2).andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets[0].totalSeats").value(2))
                .andExpect(jsonPath("$.tickets[0].label").value("Early bird"));
    }

    @Test
    void aTicketCategory_canBeRemovedOnlyWhileNobodyBookedIt() throws Exception {
        eventService.addTicketCategory(concert, "VIP", new BigDecimal("40.00"), 5);
        Long vip = eventService.ticketCategoriesOf(concert).stream()
                .filter(t -> t.label().equals("VIP")).findFirst().orElseThrow().id();
        book();

        send(delete(BASE + "/" + concert.getId() + "/tickets/" + standard), adminToken, "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TICKET_IN_USE"));
        send(delete(BASE + "/" + concert.getId() + "/tickets/" + vip), adminToken, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tickets.length()").value(1));
        assertThat(audited("TICKET_REMOVED")).isEqualTo(1);
    }

    @Test
    void cancelling_hidesTheEvent_andCancelsUnpaidBookings_butKeepsPaidOnes() throws Exception {
        Registration unpaid = book();
        Registration paid = book();
        jdbcTemplate.update("UPDATE registrations SET status = 'PAID' WHERE id = ?", paid.getId());

        send(post(BASE + "/" + concert.getId() + "/cancel"), adminToken, "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/events/" + concert.getId())).andExpect(status().isNotFound());
        mockMvc.perform(get("/asbls/mon-club/events/rss"))
                .andExpect(content().string(not(containsString("/events/" + concert.getId()))));
        assertThat(statusOf(unpaid)).isEqualTo("CANCELLED");
        assertThat(statusOf(paid)).isEqualTo("PAID");
        assertThat(audited("EVENT_CANCELLED")).isEqualTo(1);

        // History now: no more changes, and it can't be cancelled twice.
        send(put(BASE + "/" + concert.getId()), adminToken, EDIT)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_NOT_EDITABLE"));
        editTicket(standard, 20).andExpect(status().isConflict());
        send(post(BASE + "/" + concert.getId() + "/cancel"), adminToken, "").andExpect(status().isConflict());
    }

    @Test
    void onlyADraft_canBeDeleted() throws Exception {
        Event draft = eventService.createEvent(club, "Draft", null, Instant.parse("2026-12-02T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(draft, "Standard", new BigDecimal("5.00"), 10);

        send(delete(BASE + "/" + concert.getId()), adminToken, "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_NOT_EDITABLE"));
        send(delete(BASE + "/" + draft.getId()), adminToken, "").andExpect(status().isNoContent());

        mockMvc.perform(get(BASE + "/" + draft.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        assertThat(audited("EVENT_DELETED")).isEqualTo(1); // the trace outlives the draft
    }

    @Test
    void aDraft_cannotBeCancelled_andAPublishedEventCannotBePublishedAgain() throws Exception {
        Event draft = eventService.createEvent(club, "Draft", null, Instant.parse("2026-12-02T19:00:00Z"), null, "PUBLIC");

        send(post(BASE + "/" + draft.getId() + "/cancel"), adminToken, "").andExpect(status().isConflict());
        send(post(BASE + "/" + concert.getId() + "/publish"), adminToken, "")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_NOT_EDITABLE"));
    }

    @Test
    void aTicketOfAnotherEvent_isNotFoundThroughThisOne() throws Exception {
        Event other = eventService.createEvent(club, "Other", null, Instant.parse("2026-12-02T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(other, "Theirs", new BigDecimal("1.00"), 10);
        Long theirs = eventService.ticketCategoriesOf(other).get(0).id();

        send(put(BASE + "/" + concert.getId() + "/tickets/" + theirs), adminToken,
                "{\"label\": \"Hijack\", \"price\": 0, \"totalSeats\": 1}").andExpect(status().isNotFound());
        send(delete(BASE + "/" + concert.getId() + "/tickets/" + theirs), adminToken, "")
                .andExpect(status().isNotFound());
    }

    @Test
    void members_cannotChangeTheLifecycle() throws Exception {
        send(put(BASE + "/" + concert.getId()), memberToken, EDIT).andExpect(status().isForbidden());
        send(post(BASE + "/" + concert.getId() + "/cancel"), memberToken, "").andExpect(status().isForbidden());
        send(delete(BASE + "/" + concert.getId()), memberToken, "").andExpect(status().isForbidden());
        send(put(BASE + "/" + concert.getId() + "/tickets/" + standard), memberToken,
                "{\"label\": \"Free\", \"price\": 0, \"totalSeats\": 10}").andExpect(status().isForbidden());
        send(delete(BASE + "/" + concert.getId() + "/tickets/" + standard), memberToken, "")
                .andExpect(status().isForbidden());
    }

    private Registration book() {
        return reservationService.reserve(concert, standard, member);
    }

    private ResultActions editTicket(Long ticket, int seats) throws Exception {
        return send(put(BASE + "/" + concert.getId() + "/tickets/" + ticket), adminToken,
                "{\"label\": \"Early bird\", \"price\": 10.00, \"totalSeats\": %d}".formatted(seats));
    }

    private String statusOf(Registration registration) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM registrations WHERE id = ?", String.class,
                registration.getId());
    }

    private int audited(String action) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND asbl_id = ?",
                Integer.class, action, club.getId());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String token, String json) throws Exception {
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
