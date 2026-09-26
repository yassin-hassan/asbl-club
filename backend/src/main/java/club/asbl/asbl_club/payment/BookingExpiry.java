package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.event.EventService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// A booking takes its seat at once, so nobody can buy a seat that is being paid for. A booking left unpaid gives it
// back after a while: otherwise anyone clicking "Book" and walking away would hold a seat forever. Stripe's webhook
// may confirm a payment at that very moment: the booking row settles it (see RegistrationRepository), and a payment
// that arrives for an expired booking is refunded (PaymentService).
@Component
class BookingExpiry {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiry.class);

    private final RegistrationRepository registrationRepository;
    private final EventService eventService;
    private final AuditService auditService;
    private final TransactionTemplate transaction;
    private final Duration expireAfter;

    BookingExpiry(RegistrationRepository registrationRepository, EventService eventService,
            AuditService auditService, TransactionTemplate transaction,
            @Value("${bookings.expire-after}") Duration expireAfter) {
        this.registrationRepository = registrationRepository;
        this.eventService = eventService;
        this.auditService = auditService;
        this.transaction = transaction;
        this.expireAfter = expireAfter;
    }

    @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.MINUTES)
    void expireOverdue() {
        int expired = expireReservedBefore(Instant.now().minus(expireAfter));
        if (expired > 0) {
            log.info("Expired {} unpaid bookings older than {} minutes", expired, expireAfter.toMinutes());
        }
    }

    // One transaction per booking: one that fails doesn't hold back the others (it's retried at the next run).
    int expireReservedBefore(Instant cutoff) {
        List<Long> overdue = registrationRepository.findReservedBefore(cutoff);
        int expired = 0;
        for (Long id : overdue) {
            if (Boolean.TRUE.equals(transaction.execute(status -> expire(id)))) {
                expired++;
            }
        }
        return expired;
    }

    private boolean expire(Long id) {
        if (registrationRepository.expireIfReserved(id) == 0) {
            return false; // paid (or cancelled) since the list was read
        }
        Registration booking = registrationRepository.findByIdWithEventAndAsbl(id).orElseThrow();
        eventService.releaseSeat(booking.getTicketCategory().getId());
        auditService.recordSystem("BOOKING_EXPIRED", booking.getEvent().getAsbl(), "Registration", id,
                Map.of("event", booking.getEvent().getId(), "ticket", booking.getTicketCategory().getLabel()));
        return true;
    }
}
