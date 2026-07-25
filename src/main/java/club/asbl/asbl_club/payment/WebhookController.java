package club.asbl.asbl_club.payment;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final StripeProperties stripeProperties;

    WebhookController(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @PostMapping("/webhooks/stripe")
    ResponseEntity<String> handle(@RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, stripeProperties.webhookSecret());
        } catch (JsonSyntaxException e) {
            log.warn("Unreadable Stripe webhook payload");
            return ResponseEntity.badRequest().body("Invalid payload");
        } catch (SignatureVerificationException e) {
            log.warn("Invalid Stripe webhook signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }
        log.info("Received Stripe webhook: {} ({})", event.getType(), event.getId());
        return ResponseEntity.ok("ok");
    }
}
