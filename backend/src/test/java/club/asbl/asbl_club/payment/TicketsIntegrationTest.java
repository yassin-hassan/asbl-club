package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
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

// A paid booking is a ticket: its owner sees its code (for the QR code), and at the door it gets in once.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketsIntegrationTest {

    private static final String CODE = "0123456789abcdef0123456789abcdef";

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
    Event concert;
    Long standard;
    User alice;
    User bob;
    Registration paid;
    Registration unpaid;

    @BeforeEach
    void bobHasAPaidTicket_andAnUnpaidOne() {
        alice = userService.register("Alice Admin", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        bob = userService.register("Bob Buyer", "bob@club.test", "password123");
        member(bob, "MEMBER");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2026-12-01T19:00:00Z"), "Hall", "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 10);
        standard = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
        paid = reservationService.reserve(concert, standard, bob);
        unpaid = reservationService.reserve(concert, standard, bob);
        entityManager.flush();
        jdbcTemplate.update("UPDATE registrations SET status = 'PAID', qr_token = ? WHERE id = ?", CODE, paid.getId());
        entityManager.clear(); // as a new request would
    }

    @Test
    void myBookings_listMine_withTheTicketCodeOnlyOncePaid() throws Exception {
        call(get("/api/v1/registrations"), "bob@club.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id == %d)].ticketCode".formatted(paid.getId())).value(hasItem(CODE)))
                .andExpect(jsonPath("$[?(@.id == %d)].ticketCode".formatted(unpaid.getId())).value(hasItem(nullValue())))
                .andExpect(jsonPath("$[0].eventTitle").value("Concert"))
                .andExpect(jsonPath("$[0].asblName").value("Mon Club"))
                .andExpect(jsonPath("$[0].location").value("Hall"));

        // Someone else sees only their own (none): no ticket codes of others.
        call(get("/api/v1/registrations"), "alice@club.test").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aPaidTicket_getsInOnce_andIsAudited() throws Exception {
        checkIn(CODE, "alice@club.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CHECKED_IN"))
                .andExpect(jsonPath("$.name").value("Bob Buyer"))
                .andExpect(jsonPath("$.ticketLabel").value("Standard"));
        String firstTime = JsonPath.read(checkIn(CODE, "alice@club.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ALREADY_CHECKED_IN"))
                .andReturn().getResponse().getContentAsString(), "$.checkedInAt");

        assertThat(firstTime).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM registrations WHERE id = ?", String.class,
                paid.getId())).isEqualTo("ATTENDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'TICKET_CHECKED_IN' AND entity_id = ?",
                Integer.class, paid.getId())).isEqualTo(1);
    }

    // Typed as the ticket prints it: groups of four separated by spaces.
    @Test
    void aCodeTypedAsPrinted_withSpaces_matches() throws Exception {
        checkIn("0123 4567 89ab cdef 0123 4567 89ab cdef", "alice@club.test")
                .andExpect(jsonPath("$.outcome").value("CHECKED_IN"));
    }

    @Test
    void aScannedCode_withCapitalsDashesOrALineBreak_stillMatches() throws Exception {
        checkIn("  0123-4567-89AB-CDEF-0123-4567-89AB-CDEF\n", "alice@club.test")
                .andExpect(jsonPath("$.outcome").value("CHECKED_IN"));
    }

    @Test
    void aRefundedTicket_isRefused() throws Exception {
        jdbcTemplate.update("UPDATE registrations SET status = 'REFUNDED' WHERE id = ?", paid.getId());

        checkIn(CODE, "alice@club.test")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TICKET_NOT_VALID"));
    }

    @Test
    void anUnknownCode_orOneFromAnotherEvent_isNotFound() throws Exception {
        checkIn("ffffffffffffffffffffffffffffffff", "alice@club.test").andExpect(status().isNotFound());

        Event other = eventService.createEvent(club, "Other", null, Instant.parse("2026-12-02T19:00:00Z"), null, "PUBLIC");
        eventService.publish(other);
        call(post("/api/v1/asbls/mon-club/manage/events/" + other.getId() + "/check-ins")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + CODE + "\"}"), "alice@club.test")
                .andExpect(status().isNotFound());
    }

    @Test
    void checkingIn_isForAdministratorsAndTreasurers() throws Exception {
        checkIn(CODE, "bob@club.test").andExpect(status().isForbidden()); // a plain member, even the ticket's owner
        User trevor = userService.register("Trevor", "trevor@club.test", "password123");
        member(trevor, "TREASURER");
        checkIn(CODE, "trevor@club.test").andExpect(jsonPath("$.outcome").value("CHECKED_IN"));
    }

    @Test
    void aCancelledEvent_takesNoOneIn() throws Exception {
        eventService.cancel(eventService.findEvent(club, concert.getId()).orElseThrow());

        checkIn(CODE, "alice@club.test")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVENT_NOT_OPEN"));
    }

    private ResultActions checkIn(String code, String email) throws Exception {
        return call(post("/api/v1/asbls/mon-club/manage/events/" + concert.getId() + "/check-ins")
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + code.replace("\n", "\\n") + "\"}"),
                email);
    }

    private void member(User user, String role) {
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                user.getId(), club.getId(), role);
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String email) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + tokenFor(email)));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
