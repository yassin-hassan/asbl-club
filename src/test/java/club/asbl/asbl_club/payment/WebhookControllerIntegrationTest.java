package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.Stripe;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "stripe.webhook-secret=whsec_test_secret_abcdef0123456789"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class WebhookControllerIntegrationTest {

    private static final String SECRET = "whsec_test_secret_abcdef0123456789";

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

    @Test
    void succeededWebhook_finalizesThePaymentAndAudits() throws Exception {
        String intentId = "pi_hook_ok";
        Payment payment = seedInitiatedPayment("club-ok", "ok@club.test", "0101.101.101", intentId);

        mockMvc.perform(signedWebhook("payment_intent.succeeded", intentId))
                .andExpect(status().isOk());

        assertThat(paymentRepository.findByStripePaymentIntentId(intentId).orElseThrow().getStatus())
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void failedWebhook_marksThePaymentFailed() throws Exception {
        String intentId = "pi_hook_ko";
        seedInitiatedPayment("club-ko", "ko@club.test", "0202.202.202", intentId);

        mockMvc.perform(signedWebhook("payment_intent.payment_failed", intentId))
                .andExpect(status().isOk());

        assertThat(paymentRepository.findByStripePaymentIntentId(intentId).orElseThrow().getStatus())
                .isEqualTo("FAILED");
    }

    @Test
    void webhookWithABadSignature_isRejected() throws Exception {
        String payload = eventPayload("payment_intent.succeeded", "pi_whatever");
        String badSignature = signature(payload, "the-wrong-secret", Instant.now().getEpochSecond());

        mockMvc.perform(post("/webhooks/stripe")
                        .header("Stripe-Signature", badSignature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    private Payment seedInitiatedPayment(String slug, String email, String bce, String intentId) {
        User admin = userService.register("Admin", email, "password123");
        Asbl club = asblService.createAsbl(admin, "Club " + slug, bce, slug, "fr");
        Event event = eventService.createEvent(club, "Soirée", "desc",
                Instant.parse("2026-09-01T18:00:00Z"), "Bruxelles", "PUBLIC");
        eventService.addTicketCategory(event, "Standard", new BigDecimal("12.00"), 50);
        Long categoryId = eventService.ticketCategoriesOf(event).get(0).id();
        Registration registration = reservationService.reserve(categoryId, admin);

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
        payment.setStatus("INITIATED");
        return paymentRepository.save(payment);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedWebhook(
            String type, String intentId) {
        String payload = eventPayload(type, intentId);
        String signature = signature(payload, SECRET, Instant.now().getEpochSecond());
        return post("/webhooks/stripe")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }

    private static String eventPayload(String type, String intentId) {
        return "{\"id\":\"evt_test\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
                + "\",\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"id\":\"" + intentId + "\",\"object\":\"payment_intent\"}}}";
    }

    private static String signature(String payload, String secret, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
