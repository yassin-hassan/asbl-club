package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    EventService(EventRepository eventRepository, TicketCategoryRepository ticketCategoryRepository) {
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
    }

    @Transactional
    public Event createEvent(Asbl asbl, String title, String description, Instant startsAt,
            String location, String visibility) {
        Event event = new Event();
        event.setAsbl(asbl);
        event.setTitle(title);
        event.setDescription(description);
        event.setStartsAt(startsAt);
        event.setLocation(location);
        event.setVisibility(visibility);
        event.setStatus("DRAFT");
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<EventSummary> eventsOf(Asbl asbl) {
        return eventRepository.findByAsbl(asbl).stream()
                .map(e -> new EventSummary(e.getId(), e.getTitle(), e.getStartsAt(), e.getStatus(), e.getVisibility()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Event> findEvent(Asbl asbl, Long eventId) {
        return eventRepository.findByIdAndAsbl(eventId, asbl);
    }

    @Transactional
    public void addTicketCategory(Event event, String label, BigDecimal price, int totalSeats) {
        TicketCategory category = new TicketCategory();
        category.setEvent(event);
        category.setLabel(label);
        category.setPrice(price);
        category.setTotalSeats(totalSeats);
        category.setSoldSeats(0);
        ticketCategoryRepository.save(category);
    }

    @Transactional(readOnly = true)
    public List<TicketCategorySummary> ticketCategoriesOf(Event event) {
        return ticketCategoryRepository.findByEvent(event).stream()
                .map(c -> new TicketCategorySummary(c.getId(), c.getLabel(), c.getPrice(),
                        c.getTotalSeats(), c.getSoldSeats()))
                .toList();
    }
}
