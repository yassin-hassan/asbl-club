package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

// How Stripe's at-least-once delivery is absorbed: a repeated event runs its handler once (counted on a spy, since
// today's payment rules would hide a second run: the guarantee is for any handler, e.g. one that sends an email).
// Two cases need real, committed transactions: two copies at the same moment, and a failure halfway through. So this test isn't @Transactional; its data stays in the throwaway test database
// under unique names (the association can't be deleted anyway: its audit entries are append-only).
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "stripe.webhook-secret=" + StripeWebhooks.SECRET
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class WebhookIdempotencyTest {

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
    PaymentRepository paymentRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @MockitoSpyBean
    PaymentService paymentService;

    String unique;
    String intentId;
    Payment payment;

    @BeforeEach
    void anInitiatedPayment() {
        unique = String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000));
        intentId = "pi_" + unique;
        User buyer = userService.register("Buyer", "buyer-" + unique + "@hook.test", "password123");
        Asbl club = asblService.createAsbl(buyer, "Hook Club", "0" + unique.substring(0, 3) + "."
                + unique.substring(3, 6) + "." + unique.substring(6, 9), "hook-club-" + unique, "fr");
        Event event = eventService.createEvent(club, "Soirée", null, Instant.parse("2026-12-01T19:00:00Z"), null,
                "PUBLIC");
        eventService.addTicketCategory(event, "Standard", new BigDecimal("12.00"), 50);
        Registration registration = reservationService.reserve(event,
                eventService.ticketCategoriesOf(event).get(0).id(), buyer);

        Payment initiated = new Payment();
        initiated.setAsbl(club);
        initiated.setUser(buyer);
        initiated.setPayerName("Buyer");
        initiated.setPayerEmail(buyer.getEmail());
        initiated.setPayable(registration);
        initiated.setStripePaymentIntentId(intentId);
        initiated.setIdempotencyKey("payable-" + registration.getId());
        initiated.setAmount(new BigDecimal("12.00"));
        initiated.setCommission(new BigDecimal("0.66"));
        initiated.setStatus(PaymentStatus.INITIATED);
        payment = paymentRepository.save(initiated);
    }

    // Stripe retries a delivery it thinks failed (our answer was lost, too slow…) with the same event ID.
    @Test
    void theSameEventDeliveredTwice_isHandledOnce() throws Exception {
        String eventId = "evt_twice_" + unique;

        mockMvc.perform(StripeWebhooks.signed(eventId, "payment_intent.succeeded", intentId))
                .andExpect(status().isOk());
        mockMvc.perform(StripeWebhooks.signed(eventId, "payment_intent.succeeded", intentId))
                .andExpect(status().isOk()); // a repeat is still a success for Stripe: it stops retrying

        verify(paymentService, times(1)).handleSucceeded(intentId);
        assertThat(count("SELECT count(*) FROM processed_webhook_events WHERE event_id = ?", eventId)).isEqualTo(1);
    }

    @Test
    void twoCopiesOfAnEventAtTheSameMoment_areHandledOnce() throws Exception {
        String eventId = "evt_same_" + unique;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> deliveries = List.of(
                pool.submit(() -> deliver(start, eventId)),
                pool.submit(() -> deliver(start, eventId)));
        start.countDown(); // both go now
        for (Future<Integer> delivery : deliveries) {
            assertThat(delivery.get(30, TimeUnit.SECONDS)).isEqualTo(200); // both answered OK: Stripe stops
        }
        pool.shutdown();

        verify(paymentService, times(1)).handleSucceeded(intentId);
        assertThat(count("SELECT count(*) FROM processed_webhook_events WHERE event_id = ?", eventId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_SUCCEEDED' AND entity_id = ?",
                payment.getId())).isEqualTo(1);
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
    }

    // The event's record and its effect commit together, or not at all: a failure leaves no trace that would make
    // Stripe's retry look like a duplicate.
    @Test
    void aFailureWhileHandling_leavesTheEventNew_soStripesRetrySucceeds() throws Exception {
        String eventId = "evt_retry_" + unique;
        doThrow(new IllegalStateException("database hiccup")).doCallRealMethod()
                .when(paymentService).handleSucceeded(intentId);

        // Our side fails: the error reaches Stripe as a 5xx (MockMvc shows it as the exception itself).
        assertThatThrownBy(() -> mockMvc.perform(StripeWebhooks.signed(eventId, "payment_intent.succeeded", intentId)))
                .hasRootCauseMessage("database hiccup");
        assertThat(count("SELECT count(*) FROM processed_webhook_events WHERE event_id = ?", eventId)).isZero();

        // Stripe retries the same event: this time it's handled.
        mockMvc.perform(StripeWebhooks.signed(eventId, "payment_intent.succeeded", intentId))
                .andExpect(status().isOk());
        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(count("SELECT count(*) FROM processed_webhook_events WHERE event_id = ?", eventId)).isEqualTo(1);
    }

    private int deliver(CountDownLatch start, String eventId) throws Exception {
        start.await();
        return mockMvc.perform(StripeWebhooks.signed(eventId, "payment_intent.succeeded", intentId))
                .andReturn().getResponse().getStatus();
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }
}
