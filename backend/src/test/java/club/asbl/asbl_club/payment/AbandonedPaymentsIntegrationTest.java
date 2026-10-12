package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.service.PaymentIntentService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// A booking that expires or is cancelled before being paid: its payment, still open at Stripe, is cancelled there
// too, so the payment form left open in a tab can't be paid any more (annulerPaiement() in the analysis). Unless
// Stripe is already processing it: its outcome then arrives by webhook. Stripe is a stand-in.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class AbandonedPaymentsIntegrationTest {

    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    EventService eventService;
    @Autowired
    ReservationService reservationService;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    AbandonedPayments abandonedPayments;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @MockitoBean
    StripeClient stripe;

    PaymentIntentService intents = mock(PaymentIntentService.class);
    Asbl club;
    Event concert;
    Long standard;
    User bob;

    @BeforeEach
    void aConcertWithStripe() {
        when(stripe.paymentIntents()).thenReturn(intents);
        User admin = userService.register("Alice", "alice@club.test", "password-1234567");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        asblService.linkStripeAccount(club, "acct_test");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2030-12-01T19:00:00Z"), "Hall",
                "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.00"), 10);
        eventService.publish(concert);
        standard = eventService.ticketCategoriesOf(concert).get(0).id();
        bob = userService.register("Bob", "bob@club.test", "password-1234567");
    }

    @Test
    void anExpiredBookingsOpenPayment_isCancelledAtStripe() throws Exception {
        Payment payment = bookedAndStarted("pi_open", "EXPIRED");
        stripeSays("pi_open", "requires_payment_method");

        assertThat(abandonedPayments.cancelAll()).isEqualTo(1);

        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(intents).cancel(eq("pi_open"), any(PaymentIntentCancelParams.class), options.capture());
        assertThat(options.getValue().getStripeAccount()).isEqualTo("acct_test");
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("cancel-" + payment.getId());
        assertThat(statusOf(payment)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_CANCELLED'",
                Integer.class)).isEqualTo(1);
        assertThat(abandonedPayments.cancelAll()).as("done once").isZero();
    }

    // Given up by the buyer, or cancelled with its event: the same.
    @Test
    void aCancelledBookingsOpenPayment_isCancelledToo() throws Exception {
        Payment payment = bookedAndStarted("pi_given_up", "CANCELLED");
        stripeSays("pi_given_up", "requires_payment_method");

        assertThat(abandonedPayments.cancelAll()).isEqualTo(1);
        assertThat(statusOf(payment)).isEqualTo("CANCELLED");
    }

    // Already being paid when the booking expired (a Bancontact approval in progress): left to the webhook, which
    // takes the seat back or refunds.
    @Test
    void aPaymentStripeIsAlreadyProcessing_isLeftToTheWebhook() throws Exception {
        Payment payment = bookedAndStarted("pi_processing", "EXPIRED");
        stripeSays("pi_processing", "processing");

        assertThat(abandonedPayments.cancelAll()).isZero();
        verify(intents, never()).cancel(any(String.class), any(PaymentIntentCancelParams.class),
                any(RequestOptions.class));
        assertThat(statusOf(payment)).isEqualTo("INITIATED");
    }

    @Test
    void aBookingStillWaitingForItsPayment_isLeftAlone() throws Exception {
        Payment payment = bookedAndStarted("pi_waiting", "RESERVED");

        assertThat(abandonedPayments.cancelAll()).isZero();
        verify(intents, never()).retrieve(any(String.class), any(RequestOptions.class));
        assertThat(statusOf(payment)).isEqualTo("INITIATED");
    }

    @Test
    void whenStripeCantBeReached_thePaymentStaysOpen_andIsTriedAgain() throws Exception {
        Payment payment = bookedAndStarted("pi_later", "EXPIRED");
        PaymentIntent open = intent("requires_payment_method");
        when(intents.retrieve(eq("pi_later"), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("Stripe is down"))
                .thenReturn(open);

        assertThat(abandonedPayments.cancelAll()).isZero();
        assertThat(statusOf(payment)).isEqualTo("INITIATED");
        assertThat(abandonedPayments.cancelAll()).isEqualTo(1);
        assertThat(statusOf(payment)).isEqualTo("CANCELLED");
    }

    private Payment bookedAndStarted(String paymentIntent, String bookingStatus) {
        Registration booking = reservationService.reserve(concert, standard, bob);
        Payment payment = new Payment();
        payment.setAsbl(club);
        payment.setUser(bob);
        payment.setPayerName("Bob");
        payment.setPayerEmail("bob@club.test");
        payment.setPayable(booking);
        payment.setStripePaymentIntentId(paymentIntent);
        payment.setIdempotencyKey("payable-" + booking.getId());
        payment.setAmount(new BigDecimal("12.00"));
        payment.setCommission(new BigDecimal("0.66"));
        payment.setStatus(PaymentStatus.INITIATED);
        paymentRepository.save(payment);
        entityManager.flush();
        jdbcTemplate.update("UPDATE registrations SET status = ? WHERE id = ?", bookingStatus, booking.getId());
        entityManager.clear();
        return payment;
    }

    private void stripeSays(String paymentIntent, String status) throws Exception {
        PaymentIntent intent = intent(status);
        when(intents.retrieve(eq(paymentIntent), any(RequestOptions.class))).thenReturn(intent);
    }

    private static PaymentIntent intent(String status) {
        PaymentIntent intent = new PaymentIntent();
        intent.setStatus(status);
        return intent;
    }

    private String statusOf(Payment payment) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM payments WHERE id = ?", String.class, payment.getId());
    }
}
