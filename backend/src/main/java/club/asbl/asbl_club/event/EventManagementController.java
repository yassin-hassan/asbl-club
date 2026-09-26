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
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    private record Access(Asbl asbl, String role) {
        boolean admin() {
            return "ADMIN".equals(role);
        }

        // Who booked, and whether they paid, is for those who run the event and handle its money.
        boolean seesAttendees() {
            return admin() || "TREASURER".equals(role);
        }
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
        return detail(eventOf(access, eventId), access.admin(), access.seesAttendees());
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
        lifecycle(() -> eventService.publish(event));
        return detail(event, true);
    }

    @Operation(operationId = "updateEvent", summary = "Edit a draft or published event (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "The updated event", content = @Content(schema = @Schema(implementation = Detail.class)))
    @ApiResponse(responseCode = "409", description = "Cancelled or past event (EVENT_NOT_EDITABLE)",
            content = @Content(mediaType = "application/problem+json"))
    @PutMapping("/{eventId}")
    Detail update(@PathVariable String slug, @PathVariable Long eventId, @Valid @RequestBody CreateEventRequest request,
            Authentication authentication) {
        Event event = eventOf(asAdmin(slug, authentication), eventId);
        lifecycle(() -> eventService.update(event, request.title(), request.description(), request.startsAt(),
                request.location(), request.visibility()));
        return detail(event, true);
    }

    // An action, like publishing: cancelling is a step in the lifecycle (published → cancelled).
    @Operation(operationId = "cancelEvent", summary = "Cancel a published event: hidden and no longer bookable (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "The cancelled event", content = @Content(schema = @Schema(implementation = Detail.class)))
    @ApiResponse(responseCode = "409", description = "Not a published event (EVENT_NOT_EDITABLE)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/{eventId}/cancel")
    Detail cancel(@PathVariable String slug, @PathVariable Long eventId, Authentication authentication) {
        Event event = eventOf(asAdmin(slug, authentication), eventId);
        lifecycle(() -> eventService.cancel(event));
        return detail(event, true);
    }

    @Operation(operationId = "deleteDraftEvent", summary = "Delete a draft event (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "409", description = "Not a draft (EVENT_NOT_EDITABLE): cancel a published event instead",
            content = @Content(mediaType = "application/problem+json"))
    @DeleteMapping("/{eventId}")
    ResponseEntity<Void> deleteDraft(@PathVariable String slug, @PathVariable Long eventId, Authentication authentication) {
        Event event = eventOf(asAdmin(slug, authentication), eventId);
        lifecycle(() -> eventService.deleteDraft(event));
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "updateTicketCategory", summary = "Change a ticket category (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "The updated event", content = @Content(schema = @Schema(implementation = Detail.class)))
    @ApiResponse(responseCode = "409", description = "Fewer seats than taken (SEATS_BELOW_SOLD), or event not editable",
            content = @Content(mediaType = "application/problem+json"))
    @PutMapping("/{eventId}/tickets/{ticketId}")
    Detail updateTicket(@PathVariable String slug, @PathVariable Long eventId, @PathVariable Long ticketId,
            @Valid @RequestBody AddTicketRequest request, Authentication authentication) {
        Event event = eventOf(asAdmin(slug, authentication), eventId);
        lifecycle(() -> eventService.updateTicketCategory(event, ticketId, request.label(), request.price(),
                request.totalSeats()));
        return detail(event, true);
    }

    @Operation(operationId = "removeTicketCategory", summary = "Remove a ticket category nobody booked (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "The updated event", content = @Content(schema = @Schema(implementation = Detail.class)))
    @ApiResponse(responseCode = "409", description = "Already booked (TICKET_IN_USE), or event not editable",
            content = @Content(mediaType = "application/problem+json"))
    @DeleteMapping("/{eventId}/tickets/{ticketId}")
    Detail removeTicket(@PathVariable String slug, @PathVariable Long eventId, @PathVariable Long ticketId,
            Authentication authentication) {
        Event event = eventOf(asAdmin(slug, authentication), eventId);
        lifecycle(() -> eventService.removeTicketCategory(event, ticketId));
        return detail(event, true);
    }

    // The lifecycle rules' outcomes as HTTP answers; a stable "code" tells the client which rule said no.
    private static void lifecycle(Runnable change) {
        try {
            change.run();
        } catch (TicketNotInEventException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (EventNotEditableException e) {
            throw conflict("EVENT_NOT_EDITABLE", "This event can't be changed in its current state.");
        } catch (SeatsBelowSoldException e) {
            throw conflict("SEATS_BELOW_SOLD", "There can't be fewer seats than those already taken.");
        } catch (TicketInUseException e) {
            throw conflict("TICKET_IN_USE", "This ticket category has bookings and can't be removed.");
        }
    }

    private static ErrorResponseException conflict(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }

    // Every caller but the read endpoint is an administrator.
    private Detail detail(Event event, boolean canManage) {
        return detail(event, canManage, canManage);
    }

    private Detail detail(Event event, boolean canManage, boolean canSeeAttendees) {
        var tickets = eventService.ticketCategoriesOf(event).stream()
                .map(t -> new Ticket(t.id(), t.label(), t.price(), t.totalSeats(), t.soldSeats()))
                .toList();
        return new Detail(event.getId(), event.getTitle(), event.getDescription(), event.getStartsAt(),
                event.getLocation(), event.getStatus().name(), event.getVisibility().name(), canManage, canSeeAttendees, tickets);
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
        return new Access(asbl, role);
    }

    private Access asAdmin(String slug, Authentication authentication) {
        Access access = asMember(slug, authentication);
        if (!access.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return access;
    }
}
