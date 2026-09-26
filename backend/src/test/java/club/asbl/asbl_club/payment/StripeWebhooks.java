package club.asbl.asbl_club.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.stripe.Stripe;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// Webhook requests as Stripe sends them: a JSON event with its own ID, signed with the endpoint's secret.
final class StripeWebhooks {

    static final String SECRET = "whsec_test_secret_abcdef0123456789";

    private StripeWebhooks() {
    }

    static MockHttpServletRequestBuilder signed(String eventId, String type, String intentId) {
        return request(payload(eventId, type, intentId), SECRET);
    }

    static MockHttpServletRequestBuilder request(String payload, String secret) {
        return post("/webhooks/stripe")
                .header("Stripe-Signature", signature(payload, secret, Instant.now().getEpochSecond()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload);
    }

    static String payload(String eventId, String type, String intentId) {
        return "{\"id\":\"" + eventId + "\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
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
