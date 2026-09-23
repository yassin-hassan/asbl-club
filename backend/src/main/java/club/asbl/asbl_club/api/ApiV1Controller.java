package club.asbl.asbl_club.api;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.EventFeedItem;
import club.asbl.asbl_club.event.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Public read API", description = "Associations and their public events")
class ApiV1Controller {

    private final AsblService asblService;
    private final EventService eventService;

    ApiV1Controller(AsblService asblService, EventService eventService) {
        this.asblService = asblService;
        this.eventService = eventService;
    }

    @Operation(summary = "Get an association by slug")
    @GetMapping("/asbls/{slug}")
    AsblResource asbl(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new AsblResource(asbl.getSlug(), asbl.getDenomination());
    }

    @Operation(summary = "List an association's public events")
    @GetMapping("/asbls/{slug}/events")
    List<EventFeedItem> events(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return eventService.publicFeedOf(asbl);
    }

    @Operation(summary = "Get a public event by id")
    @GetMapping("/events/{eventId}")
    EventFeedItem event(@PathVariable Long eventId) {
        return eventService.publicEvent(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
