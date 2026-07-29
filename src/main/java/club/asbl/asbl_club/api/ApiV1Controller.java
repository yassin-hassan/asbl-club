package club.asbl.asbl_club.api;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.EventFeedItem;
import club.asbl.asbl_club.event.EventService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
class ApiV1Controller {

    private final AsblService asblService;
    private final EventService eventService;

    ApiV1Controller(AsblService asblService, EventService eventService) {
        this.asblService = asblService;
        this.eventService = eventService;
    }

    @GetMapping("/asbls/{slug}")
    AsblResource asbl(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new AsblResource(asbl.getSlug(), asbl.getDenomination());
    }

    @GetMapping("/asbls/{slug}/events")
    List<EventFeedItem> events(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return eventService.publicFeedOf(asbl);
    }

    @GetMapping("/events/{eventId}")
    EventFeedItem event(@PathVariable Long eventId) {
        return eventService.publicEvent(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
