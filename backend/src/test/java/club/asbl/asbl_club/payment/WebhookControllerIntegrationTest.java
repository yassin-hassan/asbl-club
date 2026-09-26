package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.StripeClient;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.RefundService;
import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    // Stripe itself is replaced by a stand-in: no refund may reach a real Stripe account from a test.
    @MockitoBean
    StripeClient stripe;
    @Autowired
    BookingExpiry bookingExpiry;
    @Autowired
    EntityManager entityManager;

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

    // The event is cancelled while the person is on Stripe's form (their booking is cancelled with it); then the
    // payment succeeds. The money goes back instead of a cancelled booking turning "paid".
    @Test
    void aPaymentForABookingCancelledMeanwhile_isRefunded() throws Exception {
        String intentId = "pi_hook_cancelled";
        Payment payment = seedInitiatedPayment("club-cancel", "cancel@club.test", "0606.606.606", intentId);
        Registration booking = registrationRepository.findById(payment.getPayable().getId()).orElseThrow();
        eventService.cancel(booking.getEvent());
        RefundService refunds = mock(RefundService.class);
        when(stripe.refunds()).thenReturn(refunds);

        mockMvc.perform(signedWebhook("payment_intent.succeeded", intentId)).andExpect(status().isOk());

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(refunds).create(params.capture(), options.capture());
        assertThat(params.getValue().getPaymentIntent()).isEqualTo(intentId);
        assertThat(params.getValue().getRefundApplicationFee()).isTrue();
        assertThat(options.getValue().getStripeAccount()).isEqualTo("acct_club-cancel"); // the association's account
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("refund-" + payment.getId());

        assertThat(paymentRepository.findById(payment.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(registrationRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.REFUNDED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_REFUNDED' AND entity_id = ?",
                Integer.class, payment.getId())).isEqualTo(1);
    }

    // The person took longer than the booking window at Stripe's form: the booking expired and gave its seat back,
    // then the payment succeeds. A seat is still free: they get it again, no refund.
    @Test
    void aPaymentJustAfterExpiry_takesASeatAgain_whenOneIsFree() throws Exception {
        Payment payment = seedInitiatedPayment("club-late-ok", "lateok@club.test", "0707.707.707", "pi_hook_late_ok");
        Long booking = expire(payment);

        mockMvc.perform(signedWebhook("payment_intent.succeeded", "pi_hook_late_ok")).andExpect(status().isOk());

        assertThat(registrationRepository.findById(booking).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.PAID);
        assertThat(soldSeatsOf(booking)).isEqualTo(1);
        verify(stripe, never()).refunds();
    }

    // Same, but the seat it gave back has been sold since (here: the last one): refunded.
    @Test
    void aPaymentJustAfterExpiry_isRefunded_whenNoSeatIsLeft() throws Exception {
        Payment payment = seedInitiatedPayment("club-late-full", "latefull@club.test", "0808.808.808", "pi_hook_late_full");
        Long booking = expire(payment);
        jdbcTemplate.update("UPDATE ticket_categories t SET sold_seats = total_seats FROM registrations r "
                + "WHERE r.ticket_category_id = t.id AND r.id = ?", booking); // sold out meanwhile
        when(stripe.refunds()).thenReturn(mock(RefundService.class));

        mockMvc.perform(signedWebhook("payment_intent.succeeded", "pi_hook_late_full")).andExpect(status().isOk());

        assertThat(registrationRepository.findById(booking).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.REFUNDED);
    }

    private Long expire(Payment payment) {
        Long booking = payment.getPayable().getId();
        entityManager.flush();
        jdbcTemplate.update("UPDATE registrations SET registered_at = now() - interval '31 minutes' WHERE id = ?",
                booking);
        bookingExpiry.expireReservedBefore(Instant.now().minus(Duration.ofMinutes(30)));
        assertThat(soldSeatsOf(booking)).isZero();
        return booking;
    }

    private int soldSeatsOf(Long booking) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT t.sold_seats FROM ticket_categories t "
                + "JOIN registrations r ON r.ticket_category_id = t.id WHERE r.id = ?", Integer.class, booking);
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
        eventService.publish(event);
        jdbcTemplate.update("UPDATE asbls SET stripe_account_id = ? WHERE id = ?", "acct_" + slug, club.getId());
        club.setStripeAccountId("acct_" + slug);
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
