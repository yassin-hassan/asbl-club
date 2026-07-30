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

    @Transactional
    public void publish(Event event) {
        event.setStatus("PUBLISHED");
        eventRepository.save(event);
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

    @Transactional(readOnly = true)
    public Optional<Event> findPublicEvent(Long eventId) {
        return eventRepository.findByIdFetchingAsbl(eventId)
                .filter(event -> "PUBLIC".equals(event.getVisibility()) && "PUBLISHED".equals(event.getStatus()));
    }

    @Transactional(readOnly = true)
    public Optional<EventFeedItem> publicEvent(Long eventId) {
        return findPublicEvent(eventId).map(this::toFeedItem);
    }

    @Transactional(readOnly = true)
    public List<EventFeedItem> publicFeed() {
        return eventRepository.findByVisibilityAndStatusOrderByStartsAtDesc("PUBLIC", "PUBLISHED").stream()
                .map(this::toFeedItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventFeedItem> publicFeedOf(Asbl asbl) {
        return eventRepository.findByAsblAndVisibilityAndStatusOrderByStartsAtDesc(asbl, "PUBLIC", "PUBLISHED").stream()
                .map(this::toFeedItem)
                .toList();
    }

    private EventFeedItem toFeedItem(Event event) {
        return new EventFeedItem(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt());
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

    @Transactional
    public TicketCategory reserveSeat(Long ticketCategoryId) {
        TicketCategory category = ticketCategoryRepository.findById(ticketCategoryId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ticket category " + ticketCategoryId));
        if (ticketCategoryRepository.reserveOneSeat(ticketCategoryId) == 0) {
            throw new TicketSoldOutException(ticketCategoryId);
        }
        return category;
    }

    @Transactional(readOnly = true)
    public List<TicketCategorySummary> ticketCategoriesOf(Event event) {
        return ticketCategoryRepository.findByEvent(event).stream()
                .map(c -> new TicketCategorySummary(c.getId(), c.getLabel(), c.getPrice(),
                        c.getTotalSeats(), c.getSoldSeats()))
                .toList();
    }
}
