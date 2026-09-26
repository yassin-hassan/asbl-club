package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventCancelled;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.TicketCategory;
import club.asbl.asbl_club.user.User;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final EventService eventService;
    private final RegistrationRepository registrationRepository;
    private final AuditService auditService;

    ReservationService(EventService eventService, RegistrationRepository registrationRepository,
            AuditService auditService) {
        this.eventService = eventService;
        this.registrationRepository = registrationRepository;
        this.auditService = auditService;
    }

    @Transactional
    public Registration reserve(Event event, Long ticketCategoryId, User user) {
        TicketCategory category = eventService.reserveSeat(event, ticketCategoryId);

        Registration registration = new Registration();
        registration.setEvent(category.getEvent());
        registration.setTicketCategory(category);
        registration.setUser(user);
        registration.setStatus(RegistrationStatus.RESERVED);
        registration.setRegisteredAt(Instant.now());
        registration.setAmount(category.getPrice());
        registration.setCurrency("EUR");
        Registration saved = registrationRepository.save(registration);
        auditService.recordFor(user, "BOOKING_CREATED", category.getEvent().getAsbl(), "Registration", saved.getId(),
                Map.of("event", category.getEvent().getId(), "ticket", category.getLabel()));
        return saved;
    }

    // An event was cancelled: bookings not yet paid are cancelled with it (checkout then refuses them; a payment
    // already under way is refunded when Stripe reports it, see PaymentService). Paid ones stay PAID: they're
    // refunded from the association's Stripe dashboard for now. Runs inside the cancelling transaction.
    @EventListener
    void onEventCancelled(EventCancelled cancelled) {
        registrationRepository.cancelUnpaid(cancelled.eventId());
    }
}
