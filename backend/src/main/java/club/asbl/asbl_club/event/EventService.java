package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.audit.AuditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final TicketCategoryRepository ticketCategoryRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    EventService(EventRepository eventRepository, TicketCategoryRepository ticketCategoryRepository,
            AuditService auditService, ApplicationEventPublisher events) {
        this.eventRepository = eventRepository;
        this.ticketCategoryRepository = ticketCategoryRepository;
        this.auditService = auditService;
        this.events = events;
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
        event.setVisibility(EventVisibility.valueOf(visibility));
        event.setStatus(EventStatus.DRAFT);
        eventRepository.save(event);
        audit("EVENT_CREATED", event, Map.of("title", title));
        return event;
    }

    @Transactional
    public void publish(Event event) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventNotEditableException();
        }
        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);
        audit("EVENT_PUBLISHED", event, null);
    }

    // Edit a draft or a published event. A cancelled event is history: it stays as it was.
    @Transactional
    public void update(Event event, String title, String description, Instant startsAt, String location,
            String visibility) {
        requireEditable(event);
        Map<String, Object> changed = new LinkedHashMap<>();
        if (!Objects.equals(event.getTitle(), title)) changed.put("title", title);
        if (!Objects.equals(event.getDescription(), description)) changed.put("description", "changed");
        if (!Objects.equals(event.getStartsAt(), startsAt)) changed.put("startsAt", startsAt.toString());
        if (!Objects.equals(event.getLocation(), location)) changed.put("location", String.valueOf(location));
        if (event.getVisibility() != EventVisibility.valueOf(visibility)) changed.put("visibility", visibility);
        event.setTitle(title);
        event.setDescription(description);
        event.setStartsAt(startsAt);
        event.setLocation(location);
        event.setVisibility(EventVisibility.valueOf(visibility));
        eventRepository.save(event);
        if (!changed.isEmpty()) {
            audit("EVENT_UPDATED", event, changed);
        }
    }

    // Cancel a published event: it disappears from public pages and can't be booked. Other parts react on their own
    // terms through the EventCancelled message (bookings not yet paid are cancelled with it). A draft is deleted
    // instead. Paid bookings are refunded from the association's Stripe dashboard for now.
    @Transactional
    public void cancel(Event event) {
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotEditableException();
        }
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
        events.publishEvent(new EventCancelled(event.getId()));
        audit("EVENT_CANCELLED", event, null);
    }

    // A draft was never public and can't have bookings: it can simply go, with its ticket categories. Those are
    // removed here rather than left to the database's cascade, which Hibernate doesn't see: categories it already
    // holds in memory would still point at the deleted event.
    @Transactional
    public void deleteDraft(Event event) {
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventNotEditableException();
        }
        audit("EVENT_DELETED", event, Map.of("title", event.getTitle()));
        ticketCategoryRepository.deleteAll(ticketCategoryRepository.findByEvent(event));
        eventRepository.delete(event);
    }

    @Transactional(readOnly = true)
    public List<EventSummary> eventsOf(Asbl asbl) {
        return eventRepository.findByAsbl(asbl).stream()
                .map(e -> new EventSummary(e.getId(), e.getTitle(), e.getStartsAt(),
                        e.getStatus().name(), e.getVisibility().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Event> findEvent(Asbl asbl, Long eventId) {
        return eventRepository.findByIdAndAsbl(eventId, asbl);
    }

    @Transactional(readOnly = true)
    public Optional<Event> findPublicEvent(Long eventId) {
        return eventRepository.findByIdFetchingAsbl(eventId)
                .filter(event -> event.getVisibility() == EventVisibility.PUBLIC
                        && event.getStatus() == EventStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public Optional<List<SeatAvailability>> publicAvailability(Long eventId) {
        return findPublicEvent(eventId).map(event -> ticketCategoriesOf(event).stream()
                .map(t -> new SeatAvailability(t.id(), t.totalSeats() - t.soldSeats()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<EventFeedItem> publicFeed() {
        return eventRepository.findByVisibilityAndStatusOrderByStartsAtDesc(
                EventVisibility.PUBLIC, EventStatus.PUBLISHED).stream()
                .map(this::toFeedItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EventFeedItem> publicFeedOf(Asbl asbl) {
        return eventRepository.findByAsblAndVisibilityAndStatusOrderByStartsAtDesc(
                asbl, EventVisibility.PUBLIC, EventStatus.PUBLISHED).stream()
                .map(this::toFeedItem)
                .toList();
    }

    private EventFeedItem toFeedItem(Event event) {
        return new EventFeedItem(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt());
    }

    @Transactional
    public void addTicketCategory(Event event, String label, BigDecimal price, int totalSeats) {
        requireEditable(event);
        TicketCategory category = new TicketCategory();
        category.setEvent(event);
        category.setLabel(label);
        category.setPrice(price);
        category.setTotalSeats(totalSeats);
        category.setSoldSeats(0);
        ticketCategoryRepository.save(category);
        audit("TICKET_ADDED", event, Map.of("label", label, "price", price, "seats", totalSeats));
    }

    // Change a category's label, price or number of seats. The price applies to new bookings only (each booking keeps
    // the amount it was made at). Seats can't go below those already taken: checked by the database in the same
    // statement that changes them, so a booking arriving at the same moment can't slip under the new limit.
    @Transactional
    public void updateTicketCategory(Event event, Long ticketId, String label, BigDecimal price, int totalSeats) {
        requireEditable(event);
        TicketCategory category = categoryOf(event, ticketId);
        if (ticketCategoryRepository.update(ticketId, label, price, totalSeats) == 0) {
            throw new SeatsBelowSoldException();
        }
        audit("TICKET_UPDATED", event, Map.of("ticket", category.getId(), "label", label, "price", price,
                "seats", totalSeats));
    }

    // Only a category nobody ever booked can be removed: bookings keep pointing at the category they were made for.
    @Transactional
    public void removeTicketCategory(Event event, Long ticketId) {
        requireEditable(event);
        TicketCategory category = categoryOf(event, ticketId);
        if (ticketCategoryRepository.hasBookings(ticketId)) {
            throw new TicketInUseException();
        }
        ticketCategoryRepository.delete(category);
        audit("TICKET_REMOVED", event, Map.of("label", category.getLabel()));
    }

    private TicketCategory categoryOf(Event event, Long ticketId) {
        return ticketCategoryRepository.findById(ticketId)
                .filter(c -> c.getEvent().getId().equals(event.getId()))
                .orElseThrow(() -> new TicketNotInEventException(ticketId));
    }

    private static void requireEditable(Event event) {
        if (event.getStatus() != EventStatus.DRAFT && event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotEditableException();
        }
    }

    private void audit(String action, Event event, Map<String, Object> payload) {
        auditService.record(action, event.getAsbl(), "Event", event.getId(), payload);
    }

    @Transactional
    public TicketCategory reserveSeat(Event event, Long ticketCategoryId) {
        // The ticket must belong to the event being booked: a ticket ID from another event (or another
        // association) must not be bookable through this one.
        TicketCategory category = ticketCategoryRepository.findById(ticketCategoryId)
                .filter(c -> c.getEvent().getId().equals(event.getId()))
                .orElseThrow(() -> new TicketNotInEventException(ticketCategoryId));
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
