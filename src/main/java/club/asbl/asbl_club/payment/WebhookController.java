package club.asbl.asbl_club.payment;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import java.util.Optional;
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
    private final PaymentService paymentService;

    WebhookController(StripeProperties stripeProperties, PaymentService paymentService) {
        this.stripeProperties = stripeProperties;
        this.paymentService = paymentService;
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
        switch (event.getType()) {
            case "payment_intent.succeeded" -> paymentIntentId(event).ifPresent(paymentService::handleSucceeded);
            case "payment_intent.payment_failed" -> paymentIntentId(event).ifPresent(paymentService::handleFailed);
            default -> {
            }
        }
        return ResponseEntity.ok("ok");
    }

    private static Optional<String> paymentIntentId(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(object -> object instanceof PaymentIntent)
                .map(object -> ((PaymentIntent) object).getId());
    }
}
