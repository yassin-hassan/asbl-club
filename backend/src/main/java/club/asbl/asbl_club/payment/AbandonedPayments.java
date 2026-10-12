package club.asbl.asbl_club.payment;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// A booking that expires, or that its buyer or the association cancels, before being paid gives its seat back at
// once; its payment, still open at Stripe, is cancelled here within a minute, so the payment form left open in a tab
// can't be paid any more. Not inside the expiry or the cancellation: one Stripe call per booking would hold up the
// request, and a Stripe failure would undo it. Each payment in its own transaction: one that fails stays open and the
// next run tries again (the idempotency key makes a repeat harmless).
@Component
class AbandonedPayments {

    private static final Logger log = LoggerFactory.getLogger(AbandonedPayments.class);

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    AbandonedPayments(PaymentRepository paymentRepository, PaymentService paymentService) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void cancelPending() {
        int cancelled = cancelAll();
        if (cancelled > 0) {
            log.info("Cancelled {} payments of expired or cancelled bookings at Stripe", cancelled);
        }
    }

    int cancelAll() {
        List<Long> payments = paymentRepository.findOpenOfAbandonedBookings();
        int cancelled = 0;
        for (Long id : payments) {
            try {
                if (paymentService.cancelAbandoned(id)) {
                    cancelled++;
                }
            } catch (Exception e) {
                log.warn("Cancelling payment {} at Stripe failed, will retry: {}", id, e.getMessage());
            }
        }
        return cancelled;
    }
}
