package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;

    EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
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
}
