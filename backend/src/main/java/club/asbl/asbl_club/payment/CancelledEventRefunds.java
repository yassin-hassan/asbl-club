package club.asbl.asbl_club.payment;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// When an association cancels an event, its paid tickets are refunded, within a minute. Not inside the cancelling
// request: that would mean one call to Stripe per ticket while the administrator waits, and one Stripe failure would
// undo the cancellation. Instead, the cancellation is recorded at once, and this job refunds the paid tickets of
// cancelled events one by one, each in its own transaction: a refund that fails leaves its ticket PAID, and the next
// run tries it again (the idempotency key prevents refunding twice).
@Component
class CancelledEventRefunds {

    private static final Logger log = LoggerFactory.getLogger(CancelledEventRefunds.class);

    private final RegistrationRepository registrationRepository;
    private final PaymentService paymentService;

    CancelledEventRefunds(RegistrationRepository registrationRepository, PaymentService paymentService) {
        this.registrationRepository = registrationRepository;
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void refundPending() {
        int refunded = refundAll();
        if (refunded > 0) {
            log.info("Refunded {} paid tickets of cancelled events", refunded);
        }
    }

    int refundAll() {
        List<Long> tickets = registrationRepository.findPaidOfCancelledEvents();
        int refunded = 0;
        for (Long id : tickets) {
            try {
                if (paymentService.refundCancelledEventTicket(id)) {
                    refunded++;
                }
            } catch (RuntimeException e) {
                log.warn("Refund of ticket {} failed, will retry: {}", id, e.getMessage());
            }
        }
        return refunded;
    }
}
