package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.RefundService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

// A buyer cancels their paid ticket within the event's cancellation delay (the state machine's
// Confirmée → Annulée [maintenant <= dateDebut - delaiAnnulationJours]): refunded in full, the platform keeping its
// commission, the seat back on sale. Stripe is a stand-in.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BuyerCancellationIntegrationTest {

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
    PaymentService paymentService;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @MockitoBean
    StripeClient stripe;

    RefundService refundService = mock(RefundService.class);
    Asbl club;
    Event concert;
    Long standard;
    User bob;
    String bobToken;

    // A concert in 30 days, cancellable until 7 days before.
    @BeforeEach
    void aConcertCancellableUntilAWeekBefore() throws Exception {
        when(stripe.refunds()).thenReturn(refundService);
        User admin = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        asblService.linkStripeAccount(club, "acct_test");
        concert = eventService.createEvent(club, "Concert", null, Instant.now().plus(Duration.ofDays(30))
                .truncatedTo(ChronoUnit.SECONDS), "Hall", "PUBLIC", 7);
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.00"), 10);
        standard = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
        bob = userService.register("Bob", "bob@club.test", "password123");
        bobToken = tokenFor("bob@club.test");
    }

    @Test
    void aBuyer_cancelsWithinTheDelay_isRefundedInFull_andTheSeatIsBackOnSale() throws Exception {
        Registration ticket = paidTicket("pi_bob");
        assertThat(soldSeats()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/registrations").header("Authorization", "Bearer " + bobToken))
                .andExpect(jsonPath("$[0].cancellableUntil").exists());

        cancel(ticket).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.cancellableUntil").doesNotExist());

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(refundService).create(params.capture(), options.capture());
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_bob");
        assertThat(params.getValue().getAmount()).as("the whole price").isNull();
        assertThat(params.getValue().getRefundApplicationFee()).as("the platform keeps its commission").isFalse();
        assertThat(options.getValue().getStripeAccount()).isEqualTo("acct_test");
        assertThat(options.getValue().getIdempotencyKey()).startsWith("refund-");

        assertThat(statusOf("registrations", ticket.getId())).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM payments WHERE payable_id = ?", String.class,
                ticket.getId())).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT refunded_at IS NOT NULL AND NOT commission_refunded "
                + "FROM payments WHERE payable_id = ?", Boolean.class, ticket.getId()))
                .as("refunded now, the platform keeping its commission").isTrue();
        assertThat(soldSeats()).as("the seat is back on sale").isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'BOOKING_CANCELLED' "
                + "AND user_id = ?", Integer.class, bob.getId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_REFUNDED' "
                + "AND payload ->> 'reason' = 'cancelled by buyer'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM email_outbox WHERE recipient = 'bob@club.test' "
                + "AND subject LIKE 'Billet annulé%'", String.class)).isEqualTo("Billet annulé : Concert");
    }

    @Test
    void afterTheDelay_orWithoutOne_cancellingIsRefused() throws Exception {
        Registration ticket = paidTicket("pi_late");
        // The concert moves to 5 days from now: the 7-day delay has passed.
        jdbcTemplate.update("UPDATE events SET starts_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(5))), concert.getId());
        entityManager.clear();
        cancel(ticket).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));

        // Not refundable at all.
        jdbcTemplate.update("UPDATE events SET starts_at = ?, cancellation_days = 0 WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().plus(Duration.ofDays(30))), concert.getId());
        entityManager.clear();
        cancel(ticket).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANCELLATION_CLOSED"));

        verify(refundService, never()).create(any(RefundCreateParams.class), any(RequestOptions.class));
        assertThat(statusOf("registrations", ticket.getId())).isEqualTo("PAID");
    }

    @Test
    void onlyAPaidUnusedTicket_canBeCancelled_andOnlyOnce() throws Exception {
        Registration unpaid = reservationService.reserve(concert, standard, bob);
        cancel(unpaid).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_CANCELLABLE"));

        Registration ticket = paidTicket("pi_once");
        cancel(ticket).andExpect(status().isOk());
        cancel(ticket).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_CANCELLABLE"));
        verify(refundService, times(1)).create(any(RefundCreateParams.class), any(RequestOptions.class));

        Registration used = paidTicket("pi_used");
        jdbcTemplate.update("UPDATE registrations SET status = 'ATTENDED' WHERE id = ?", used.getId());
        entityManager.clear();
        cancel(used).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_CANCELLABLE"));
    }

    @Test
    void someoneElsesTicket_isNotFound() throws Exception {
        Registration ticket = paidTicket("pi_bob");
        userService.register("Carol", "carol@club.test", "password123");

        mockMvc.perform(post("/api/v1/registrations/" + ticket.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor("carol@club.test")))
                .andExpect(status().isNotFound());
        verify(refundService, never()).create(any(RefundCreateParams.class), any(RequestOptions.class));
    }

    // Stripe is called first: if it fails, nothing changes, and the person can try again.
    @Test
    void whenStripeFails_nothingChanges() throws Exception {
        Registration ticket = paidTicket("pi_down");
        when(refundService.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("Stripe is down"))
                .thenReturn(new Refund());

        cancel(ticket).andExpect(status().isBadGateway());
        assertThat(statusOf("registrations", ticket.getId())).isEqualTo("PAID");
        assertThat(soldSeats()).isEqualTo(1);

        cancel(ticket).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    void aGuest_cancelsThroughTheirLink() throws Exception {
        ReservationService.GuestReservation guest =
                reservationService.reserveForGuest(concert, standard, "Zoé", "zoe@mail.test", "fr");
        pay(guest.registration(), "pi_guest", null);

        mockMvc.perform(get("/api/v1/guest-bookings/" + guest.accessToken()))
                .andExpect(jsonPath("$.cancellableUntil").exists());
        mockMvc.perform(post("/api/v1/guest-bookings/" + guest.accessToken() + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_outbox WHERE recipient = 'zoe@mail.test' "
                + "AND subject = 'Billet annulé : Concert'", Integer.class)).isEqualTo(1);
    }

    private Registration paidTicket(String paymentIntent) {
        Registration ticket = reservationService.reserve(concert, standard, bob);
        pay(ticket, paymentIntent, bob);
        return ticket;
    }

    private void pay(Registration ticket, String paymentIntent, User payer) {
        Payment payment = new Payment();
        payment.setAsbl(club);
        payment.setUser(payer);
        payment.setPayerName(payer == null ? "Zoé" : payer.getName());
        payment.setPayerEmail(payer == null ? "zoe@mail.test" : payer.getEmail());
        payment.setPayable(ticket);
        payment.setStripePaymentIntentId(paymentIntent);
        payment.setIdempotencyKey("payable-" + ticket.getId());
        payment.setAmount(new BigDecimal("12.00"));
        payment.setCommission(new BigDecimal("0.66"));
        payment.setStatus(PaymentStatus.INITIATED);
        paymentRepository.save(payment);
        paymentService.handleSucceeded(paymentIntent);
        entityManager.flush();
    }

    private ResultActions cancel(Registration ticket) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + ticket.getId() + "/cancel")
                .header("Authorization", "Bearer " + bobToken));
    }

    private String statusOf(String table, Long id) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    private Integer soldSeats() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT sold_seats FROM ticket_categories WHERE id = ?", Integer.class,
                standard);
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"password123\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
