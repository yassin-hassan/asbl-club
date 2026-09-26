package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "stripe.webhook-secret=" + StripeWebhooks.SECRET
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class WebhookControllerIntegrationTest {

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
    RegistrationRepository registrationRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void succeededWebhook_finalizesThePaymentAndAudits() throws Exception {
        String intentId = "pi_hook_ok";
        Payment payment = seedInitiatedPayment("club-ok", "ok@club.test", "0101.101.101", intentId);

        mockMvc.perform(signedWebhook("payment_intent.succeeded", intentId))
                .andExpect(status().isOk());

        assertThat(paymentRepository.findByStripePaymentIntentId(intentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void failedWebhook_marksThePaymentFailed() throws Exception {
        String intentId = "pi_hook_ko";
        seedInitiatedPayment("club-ko", "ko@club.test", "0202.202.202", intentId);

        mockMvc.perform(signedWebhook("payment_intent.payment_failed", intentId))
                .andExpect(status().isOk());

        assertThat(paymentRepository.findByStripePaymentIntentId(intentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);
    }

    // Events we don't act on are recorded too: a trace of everything Stripe sent.
    @Test
    void eventsWeDontActOn_areStillRecorded() throws Exception {
        mockMvc.perform(StripeWebhooks.signed("evt_other", "customer.created", "pi_none")).andExpect(status().isOk());

        assertThat(processed("evt_other")).isEqualTo(1);
    }

    // A declined card doesn't end a Stripe payment: the person may retry on the same PaymentIntent, with another card.
    // Stripe then sends "payment_failed" for the first attempt and "succeeded" for the second.
    @Test
    void aDeclinedCardFollowedByASuccessfulRetry_endsPaid() throws Exception {
        String intentId = "pi_hook_retry";
        Payment payment = seedInitiatedPayment("club-retry", "retry@club.test", "0303.303.303", intentId);

        mockMvc.perform(signedWebhook("payment_intent.payment_failed", intentId)).andExpect(status().isOk());
        mockMvc.perform(signedWebhook("payment_intent.succeeded", intentId)).andExpect(status().isOk());

        assertThat(paymentRepository.findByStripePaymentIntentId(intentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(registrationRepository.findById(payment.getPayable().getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PAID);
    }

    // Stripe doesn't promise order: the first attempt's "failed" may arrive after the retry's "succeeded".
    @Test
    void aLateFailure_doesNotUndoASuccess() throws Exception {
        String intentId = "pi_hook_late";
        Payment payment = seedInitiatedPayment("club-late", "late@club.test", "0404.404.404", intentId);

        mockMvc.perform(signedWebhook("payment_intent.succeeded", intentId)).andExpect(status().isOk());
        mockMvc.perform(signedWebhook("payment_intent.payment_failed", intentId)).andExpect(status().isOk());

        assertThat(paymentRepository.findByStripePaymentIntentId(intentId).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(registrationRepository.findById(payment.getPayable().getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PAID);
    }

    @Test
    void webhookWithABadSignature_isRejected() throws Exception {
        String payload = StripeWebhooks.payload("evt_forged", "payment_intent.succeeded", "pi_whatever");

        mockMvc.perform(StripeWebhooks.request(payload, "the-wrong-secret"))
                .andExpect(status().isBadRequest());
        assertThat(processed("evt_forged")).isZero(); // nothing recorded for an unverified request
    }

    private Payment seedInitiatedPayment(String slug, String email, String bce, String intentId) {
        User admin = userService.register("Admin", email, "password123");
        Asbl club = asblService.createAsbl(admin, "Club " + slug, bce, slug, "fr");
        Event event = eventService.createEvent(club, "Soirée", "desc",
                Instant.parse("2026-09-01T18:00:00Z"), "Bruxelles", "PUBLIC");
        eventService.addTicketCategory(event, "Standard", new BigDecimal("12.00"), 50);
        Long categoryId = eventService.ticketCategoriesOf(event).get(0).id();
        Registration registration = reservationService.reserve(event, categoryId, admin);

        Payment payment = new Payment();
        payment.setAsbl(club);
        payment.setUser(admin);
        payment.setPayerName("Admin");
        payment.setPayerEmail(email);
        payment.setPayable(registration);
        payment.setStripePaymentIntentId(intentId);
        payment.setIdempotencyKey("payable-" + registration.getId());
        payment.setAmount(new BigDecimal("12.00"));
        payment.setCommission(new BigDecimal("0.66"));
        payment.setStatus(PaymentStatus.INITIATED);
        return paymentRepository.save(payment);
    }

    // Each delivery gets its own event ID, as distinct Stripe events do; a repeat reuses one on purpose.
    private int events;

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedWebhook(
            String type, String intentId) {
        return StripeWebhooks.signed("evt_test_" + (++events), type, intentId);
    }

    private int processed(String eventId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM processed_webhook_events WHERE event_id = ?",
                Integer.class, eventId);
    }
}
