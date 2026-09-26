package club.asbl.asbl_club.payment;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Handles a verified Stripe event exactly once in effect, although Stripe may deliver it several times: the event's
// ID is recorded in the same transaction as what it changes. If handling fails, both roll back, the caller answers
// 5xx, and Stripe's retry finds the event new again. Different events about one payment (failed, then succeeded) have
// different IDs: combining them in any order is the payment rules' job (PaymentService).
@Service
class StripeEventHandler {

    private static final Logger log = LoggerFactory.getLogger(StripeEventHandler.class);

    private final ProcessedWebhookEventRepository processedEvents;
    private final PaymentService paymentService;

    StripeEventHandler(ProcessedWebhookEventRepository processedEvents, PaymentService paymentService) {
        this.processedEvents = processedEvents;
        this.paymentService = paymentService;
    }

    @Transactional
    public void handle(Event event) {
        if (processedEvents.recordIfNew(event.getId(), event.getType()) == 0) {
            log.info("Stripe event {} already handled, ignoring this delivery", event.getId());
            return;
        }
        switch (event.getType()) {
            case "payment_intent.succeeded" -> paymentIntentId(event).ifPresent(paymentService::handleSucceeded);
            case "payment_intent.payment_failed" -> paymentIntentId(event).ifPresent(paymentService::handleFailed);
            default -> {
            }
        }
    }

    private static Optional<String> paymentIntentId(Event event) {
        return event.getDataObjectDeserializer().getObject()
                .filter(object -> object instanceof PaymentIntent)
                .map(object -> ((PaymentIntent) object).getId());
    }
}
