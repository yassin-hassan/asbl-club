package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.auth.OneTimeTokens;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventCancelled;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.TicketCategory;
import club.asbl.asbl_club.user.User;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final EventService eventService;
    private final RegistrationRepository registrationRepository;
    private final AuditService auditService;
    private final BookingEmails bookingEmails;

    ReservationService(EventService eventService, RegistrationRepository registrationRepository,
            AuditService auditService, BookingEmails bookingEmails) {
        this.eventService = eventService;
        this.registrationRepository = registrationRepository;
        this.auditService = auditService;
        this.bookingEmails = bookingEmails;
    }

    /** A guest's booking and the secret link back to it (returned once, then only its hash is kept). */
    public record GuestReservation(Registration registration, String accessToken) {
    }

    // A visitor without an account books a seat on a public event, the same way (the seat is taken at once). The
    // link back to the booking is emailed in the same transaction: if they close the tab, they can still pay.
    @Transactional
    public GuestReservation reserveForGuest(Event event, Long ticketCategoryId, String name, String email,
            String language) {
        TicketCategory category = eventService.reserveSeat(event, ticketCategoryId);
        String accessToken = OneTimeTokens.newToken();

        Registration registration = new Registration();
        registration.setEvent(category.getEvent());
        registration.setTicketCategory(category);
        registration.setGuestName(name.trim());
        registration.setGuestEmail(email.trim().toLowerCase(Locale.ROOT));
        registration.setGuestLanguage(language);
        registration.setAccessTokenHash(OneTimeTokens.hash(accessToken));
        registration.setStatus(RegistrationStatus.RESERVED);
        registration.setRegisteredAt(Instant.now());
        registration.setAmount(category.getPrice());
        registration.setCurrency("EUR");
        Registration saved = registrationRepository.save(registration);
        auditService.record("BOOKING_CREATED", category.getEvent().getAsbl(), "Registration", saved.getId(),
                Map.of("event", category.getEvent().getId(), "ticket", category.getLabel(), "guest", true));
        bookingEmails.guestBooked(saved, accessToken);
        return new GuestReservation(saved, accessToken);
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
    // already under way is refunded when Stripe reports it, see PaymentService). Paid ones are refunded just after,
    // by CancelledEventRefunds (a call to Stripe each, outside this transaction). Runs inside the cancelling
    // transaction.
    @EventListener
    void onEventCancelled(EventCancelled cancelled) {
        registrationRepository.cancelUnpaid(cancelled.eventId());
    }
}
