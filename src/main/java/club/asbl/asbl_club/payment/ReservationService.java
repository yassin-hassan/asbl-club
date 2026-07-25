package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.TicketCategory;
import club.asbl.asbl_club.user.User;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private final EventService eventService;
    private final RegistrationRepository registrationRepository;

    ReservationService(EventService eventService, RegistrationRepository registrationRepository) {
        this.eventService = eventService;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public Registration reserve(Long ticketCategoryId, User user) {
        TicketCategory category = eventService.reserveSeat(ticketCategoryId);

        Registration registration = new Registration();
        registration.setEvent(category.getEvent());
        registration.setTicketCategory(category);
        registration.setUser(user);
        registration.setStatus("RESERVED");
        registration.setRegisteredAt(Instant.now());
        registration.setAmount(category.getPrice());
        registration.setCurrency("EUR");
        return registrationRepository.save(registration);
    }
}
