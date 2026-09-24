package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.ManagedEvents.Detail;
import club.asbl.asbl_club.event.ManagedEvents.EventList;
import club.asbl.asbl_club.event.ManagedEvents.Item;
import club.asbl.asbl_club.event.ManagedEvents.Ticket;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// An association's back office for events. Every call needs a login; members of the association may read
// (drafts included), only its administrators may change anything. Anyone else gets 403.
@RestController
@RequestMapping("/api/v1/asbls/{slug}/manage/events")
@Tag(name = "Event management", description = "An association's events, as seen and managed by its members")
class EventManagementController {

    private final EventService eventService;
    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;

    EventManagementController(EventService eventService, AsblService asblService, UserService userService,
            MembershipService membershipService) {
        this.eventService = eventService;
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
    }

    private record Access(Asbl asbl, boolean admin) {
    }

    @Operation(operationId = "listManagedEvents", summary = "All the association's events, drafts included (members)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping
    EventList list(@PathVariable String slug, Authentication authentication) {
        Access access = asMember(slug, authentication);
        var events = eventService.eventsOf(access.asbl()).stream()
                .map(e -> new Item(e.id(), e.title(), e.startsAt(), e.status(), e.visibility()))
                .toList();
        return new EventList(access.asbl().getSlug(), access.asbl().getDenomination(), access.admin(), events);
    }

    @Operation(operationId = "createEvent", summary = "Create an event, as a draft (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(schema = @Schema(implementation = Detail.class)))
    @PostMapping
    ResponseEntity<Detail> create(@PathVariable String slug, @Valid @RequestBody CreateEventRequest request,
            Authentication authentication) {
        Access access = asAdmin(slug, authentication);
        Event event = eventService.createEvent(access.asbl(), request.title(), request.description(),
                request.startsAt(), request.location(), request.visibility());
        return ResponseEntity.created(URI.create("/api/v1/asbls/" + slug + "/manage/events/" + event.getId()))
                .body(detail(event, true));
    }

    @Operation(operationId = "getManagedEvent", summary = "One event with its ticket categories and sales (members)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/{eventId}")
    Detail get(@PathVariable String slug, @PathVariable Long eventId, Authentication authentication) {
        Access access = asMember(slug, authentication);
        return detail(eventOf(access, eventId), access.admin());
    }

    @Operation(operationId = "addTicketCategory", summary = "Add a ticket category to an event (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Added; the updated event",
            content = @Content(schema = @Schema(implementation = Detail.class)))
    @PostMapping("/{eventId}/tickets")
    ResponseEntity<Detail> addTicket(@PathVariable String slug, @PathVariable Long eventId,
            @Valid @RequestBody AddTicketRequest request, Authentication authentication) {
        Access access = asAdmin(slug, authentication);
        Event event = eventOf(access, eventId);
        eventService.addTicketCategory(event, request.label(), request.price(), request.totalSeats());
        return ResponseEntity.status(HttpStatus.CREATED).body(detail(event, true));
    }

    // An action rather than a field update: publishing is a step in the event's lifecycle (draft → published).
    @Operation(operationId = "publishEvent", summary = "Publish a draft event (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @PostMapping("/{eventId}/publish")
    Detail publish(@PathVariable String slug, @PathVariable Long eventId, Authentication authentication) {
        Access access = asAdmin(slug, authentication);
        Event event = eventOf(access, eventId);
        eventService.publish(event);
        return detail(event, true);
    }

    private Detail detail(Event event, boolean canManage) {
        var tickets = eventService.ticketCategoriesOf(event).stream()
                .map(t -> new Ticket(t.id(), t.label(), t.price(), t.totalSeats(), t.soldSeats()))
                .toList();
        return new Detail(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt(),
                event.getLocation(), event.getStatus().name(), event.getVisibility().name(), canManage, tickets);
    }

    // The event must belong to this association: an ID from another association is simply not found here.
    private Event eventOf(Access access, Long eventId) {
        return eventService.findEvent(access.asbl(), eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Access asMember(String slug, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String role = membershipService.roleOf(user, asbl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        return new Access(asbl, "ADMIN".equals(role));
    }

    private Access asAdmin(String slug, Authentication authentication) {
        Access access = asMember(slug, authentication);
        if (!access.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return access;
    }
}
