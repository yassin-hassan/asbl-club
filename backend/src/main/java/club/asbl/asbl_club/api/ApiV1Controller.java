package club.asbl.asbl_club.api;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventFeedItem;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.SeatAvailability;
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
@Tag(name = "Public", description = "Associations and their public events, readable without logging in")
class ApiV1Controller {

    private final AsblService asblService;
    private final EventService eventService;

    ApiV1Controller(AsblService asblService, EventService eventService) {
        this.asblService = asblService;
        this.eventService = eventService;
    }

    @Operation(operationId = "getAsbl", summary = "Get an association by slug")
    @GetMapping("/asbls/{slug}")
    AsblResource asbl(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new AsblResource(asbl.getSlug(), asbl.getDenomination());
    }

    @Operation(operationId = "listAsblEvents", summary = "List an association's public events")
    @GetMapping("/asbls/{slug}/events")
    List<EventFeedItem> events(@PathVariable String slug) {
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return eventService.publicFeedOf(asbl);
    }

    @Operation(operationId = "getEvent", summary = "Get a public event with its association, location and tickets")
    @GetMapping("/events/{eventId}")
    PublicEvent event(@PathVariable Long eventId) {
        Event event = eventService.findPublicEvent(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<PublicTicket> tickets = eventService.ticketCategoriesOf(event).stream()
                .map(t -> new PublicTicket(t.id(), t.label(), t.price(), t.totalSeats() - t.soldSeats()))
                .toList();
        Asbl asbl = event.getAsbl();
        return new PublicEvent(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt(),
                event.getLocation(), new AsblResource(asbl.getSlug(), asbl.getDenomination()), tickets);
    }

    @Operation(operationId = "getEventAvailability", summary = "Remaining seats per ticket category of a public event")
    @GetMapping("/events/{eventId}/availability")
    List<SeatAvailability> availability(@PathVariable Long eventId) {
        return eventService.publicAvailability(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
