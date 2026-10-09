package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Buying a ticket for a public event without an account, as the analysis describes (the "Visiteur" actor). Stripe is
// replaced by a stand-in; everything else is real.
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.public-url=https://site.test"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class GuestBookingIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    PaymentService paymentService;

    Asbl club;
    Event concert;
    Long ticket;

    @BeforeEach
    void aPublicEventOfAnAssociationThatAcceptsPayments() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        asblService.linkStripeAccount(club, "acct_test123");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2030-12-01T19:00:00Z"), "Hall",
                "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 2);
        ticket = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
    }

    @Test
    void aVisitor_booksWithoutAnAccount_andGetsTheSecretLinkByEmail() throws Exception {
        String body = bookAsGuest(concert.getId(), ticket, "Zoé Martin", "Zoe.Martin@Mail.test", "nl")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.booking.status").value("RESERVED"))
                .andExpect(jsonPath("$.booking.name").value("Zoé Martin"))
                .andExpect(jsonPath("$.booking.email").value("zoe.martin@mail.test"))
                .andExpect(jsonPath("$.booking.amount").value(12.50))
                .andExpect(jsonPath("$.booking.ticketCode").doesNotExist()) // no ticket before paying
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        assertThat(soldSeats()).isEqualTo(1);
        // Only the link's hash is stored, never the link itself.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM registrations WHERE access_token_hash = ?",
                Integer.class, token)).isZero();
        // The link is emailed at once, in the visitor's language.
        String email = jdbcTemplate.queryForObject(
                "SELECT subject || ' / ' || body FROM email_outbox WHERE recipient = 'zoe.martin@mail.test'", String.class);
        assertThat(email).contains("Je reservering voor Concert", "https://site.test/tickets/" + token);
        // The audit log records the booking, with no account behind it.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'BOOKING_CREATED' "
                + "AND user_id IS NULL AND payload ->> 'guest' = 'true'", Integer.class)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/guest-bookings/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTitle").value("Concert"))
                .andExpect(jsonPath("$.asblName").value("Mon Club"));
    }

    @Test
    void anUnknownLink_isNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/guest-bookings/not-a-real-link")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/guest-bookings/not-a-real-link/checkout")).andExpect(status().isNotFound());
    }

    @Test
    void theLink_startsThePayment_inTheGuestsName() throws Exception {
        when(paymentService.initiate(any(), any(), any(), any(), any())).thenReturn(new PaymentInitiation(1L, "pi_secret"));
        String token = JsonPath.read(bookAsGuest(concert.getId(), ticket, "Zoé Martin", "zoe@mail.test", "fr")
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/guest-bookings/" + token + "/checkout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_secret"))
                .andExpect(jsonPath("$.stripeAccount").value("acct_test123"));
        verify(paymentService).initiate(any(), any(), eq("Zoé Martin"), eq("zoe@mail.test"), isNull());
    }

    @Test
    void membersOnlyEvents_draftsAndOtherEventsTickets_cannotBeBookedByGuests() throws Exception {
        Event members = eventService.createEvent(club, "AG", null, Instant.parse("2030-12-02T19:00:00Z"), null, "MEMBERS");
        eventService.addTicketCategory(members, "Membre", new BigDecimal("5.00"), 10);
        eventService.publish(members);
        Long membersTicket = eventService.ticketCategoriesOf(members).get(0).id();
        Event draft = eventService.createEvent(club, "Draft", null, Instant.parse("2030-12-03T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(draft, "Standard", new BigDecimal("5.00"), 10);
        Long draftTicket = eventService.ticketCategoriesOf(draft).get(0).id();

        bookAsGuest(members.getId(), membersTicket, "Zoé", "zoe@mail.test", "fr").andExpect(status().isNotFound());
        bookAsGuest(draft.getId(), draftTicket, "Zoé", "zoe@mail.test", "fr").andExpect(status().isNotFound());
        // IDOR: a ticket of another event, through this public one.
        bookAsGuest(concert.getId(), membersTicket, "Zoé", "zoe@mail.test", "fr").andExpect(status().isNotFound());
    }

    // A published event stays published once it's over: only its date closes bookings, with or without an account.
    @Test
    void anEventThatHasStarted_cannotBeBookedAnyMore() throws Exception {
        Event past = eventService.createEvent(club, "Last summer", null, Instant.now().minusSeconds(3600), null, "PUBLIC");
        eventService.addTicketCategory(past, "Standard", new BigDecimal("5.00"), 10);
        eventService.publish(past);
        Long pastTicket = eventService.ticketCategoriesOf(past).get(0).id();
        userService.register("Bob", "bob@club.test", "password123");

        bookAsGuest(past.getId(), pastTicket, "Zoé", "zoe@mail.test", "fr").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_OVER"));
        bookPublic(tokenFor("bob@club.test"), past.getId(), pastTicket).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVENT_OVER"));
        assertThat(jdbcTemplate.queryForObject("SELECT sold_seats FROM ticket_categories WHERE id = ?", Integer.class,
                pastTicket)).isZero();
    }

    @Test
    void soldOut_isRefused() throws Exception {
        bookAsGuest(concert.getId(), ticket, "Zoé", "zoe@mail.test", "fr").andExpect(status().isCreated());
        bookAsGuest(concert.getId(), ticket, "Yves", "yves@mail.test", "fr").andExpect(status().isCreated());
        bookAsGuest(concert.getId(), ticket, "Xavier", "xavier@mail.test", "fr").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));
    }

    // Nobody could pay: refused before a seat is taken or an email sent.
    @Test
    void anAssociationWithoutPayments_takesNoGuestBooking() throws Exception {
        asblService.linkStripeAccount(club, null);

        bookAsGuest(concert.getId(), ticket, "Zoé", "zoe@mail.test", "fr").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENTS_DISABLED"));
        assertThat(soldSeats()).isZero();
    }

    @Test
    void invalidDetails_areRefused() throws Exception {
        bookAsGuest(concert.getId(), ticket, "", "zoe@mail.test", "fr").andExpect(status().isBadRequest());
        bookAsGuest(concert.getId(), ticket, "Zoé", "not-an-email", "fr").andExpect(status().isBadRequest());
        bookAsGuest(concert.getId(), ticket, "Zoé", "zoe@mail.test", "de").andExpect(status().isBadRequest());
        assertThat(soldSeats()).isZero();
    }

    // With an account, from the public page: no membership needed for a public event.
    @Test
    void anyAccount_booksAPublicEvent_butNotAMembersOnlyOne() throws Exception {
        userService.register("Bob", "bob@club.test", "password123");
        String bob = tokenFor("bob@club.test");
        Event members = eventService.createEvent(club, "AG", null, Instant.parse("2030-12-02T19:00:00Z"), null, "MEMBERS");
        eventService.addTicketCategory(members, "Membre", new BigDecimal("5.00"), 10);
        eventService.publish(members);
        Long membersTicket = eventService.ticketCategoriesOf(members).get(0).id();

        bookPublic(bob, concert.getId(), ticket).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        bookPublic(bob, members.getId(), membersTicket).andExpect(status().isNotFound());
        bookPublic(null, concert.getId(), ticket).andExpect(status().isUnauthorized());
    }

    private ResultActions bookAsGuest(Long eventId, Long ticketId, String name, String email, String language)
            throws Exception {
        String json = """
                {"eventId": %d, "ticketCategoryId": %d, "name": "%s", "email": "%s", "language": "%s"}"""
                .formatted(eventId, ticketId, name, email, language);
        return mockMvc.perform(post("/api/v1/guest-bookings").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions bookPublic(String token, Long eventId, Long ticketId) throws Exception {
        var request = post("/api/v1/events/" + eventId + "/registrations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketCategoryId\": " + ticketId + "}");
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private Integer soldSeats() {
        return jdbcTemplate.queryForObject("SELECT sold_seats FROM ticket_categories WHERE id = ?", Integer.class,
                ticket);
    }
}
