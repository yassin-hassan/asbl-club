package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.EventStatus;
import club.asbl.asbl_club.payment.Registrations.CheckIn;
import club.asbl.asbl_club.payment.Registrations.CheckInRequest;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Who booked an event (on screen and as a spreadsheet) and who came (check-in at the door), for the association's
// administrators and treasurers only: names and email addresses are personal data, shown to those who need them to
// run the event and handle its money.
@RestController
@RequestMapping("/api/v1/asbls/{slug}/manage/events/{eventId}")
@Tag(name = "Event management", description = "An association's events, as seen and managed by its members")
class AttendeeController {

    private static final Set<String> ALLOWED_ROLES = Set.of("ADMIN", "TREASURER");

    private final RegistrationRepository registrationRepository;
    private final EventService eventService;
    private final AsblService asblService;
    private final MembershipService membershipService;
    private final UserService userService;
    private final AuditService auditService;
    private final MessageSource messages;

    AttendeeController(RegistrationRepository registrationRepository, EventService eventService,
            AsblService asblService, MembershipService membershipService, UserService userService,
            AuditService auditService, MessageSource messages) {
        this.registrationRepository = registrationRepository;
        this.eventService = eventService;
        this.asblService = asblService;
        this.membershipService = membershipService;
        this.userService = userService;
        this.auditService = auditService;
        this.messages = messages;
    }

    @Operation(operationId = "listAttendees", summary = "Who booked this event (administrators and treasurers)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Every booking, oldest first",
            content = @Content(schema = @Schema(implementation = Attendees.class)))
    @ApiResponse(responseCode = "403", description = "Not an administrator or treasurer of this association",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/attendees")
    @Transactional(readOnly = true)
    Attendees list(@PathVariable String slug, @PathVariable Long eventId, Authentication authentication) {
        Event event = eventFor(slug, eventId, authentication);
        return new Attendees(event.getTitle(), attendeesOf(event));
    }

    // The download is audited: personal data leaving the platform, in a file nobody can recall.
    @Operation(operationId = "exportAttendees", summary = "The attendee list as a CSV file for Excel "
            + "(administrators and treasurers)", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Semicolon-separated, UTF-8 with a byte-order mark",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "403", description = "Not an administrator or treasurer of this association",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping(value = "/attendees/export", produces = "text/csv")
    @Transactional
    ResponseEntity<byte[]> export(@PathVariable String slug, @PathVariable Long eventId,
            Authentication authentication) {
        Event event = eventFor(slug, eventId, authentication);
        List<Attendees.Attendee> attendees = attendeesOf(event);
        byte[] csv = AttendeeCsv.write(attendees, messages, LocaleContextHolder.getLocale());
        auditService.record("ATTENDEES_EXPORTED", event.getAsbl(), "Event", event.getId(),
                Map.of("rows", attendees.size()));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("attendees-" + slug + "-" + eventId + ".csv").build().toString())
                .cacheControl(CacheControl.noStore()) // personal data: no copy in any cache
                .body(csv);
    }

    // At the door: the code from a ticket's QR code, typed or scanned. A ticket gets in once; showing it again (or a
    // copy) answers ALREADY_CHECKED_IN with the time it was first used, so the person at the door can decide.
    @Operation(operationId = "checkIn", summary = "Check a ticket in at the door (administrators and treasurers)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Checked in now, or already checked in before",
            content = @Content(schema = @Schema(implementation = CheckIn.class)))
    @ApiResponse(responseCode = "404", description = "No ticket with this code for this event",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "The event isn't taking place (EVENT_NOT_OPEN), or the ticket "
            + "isn't valid any more, e.g. refunded (TICKET_NOT_VALID)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/check-ins")
    @Transactional
    CheckIn checkIn(@PathVariable String slug, @PathVariable Long eventId,
            @Valid @RequestBody CheckInRequest request, Authentication authentication) {
        Event event = eventFor(slug, eventId, authentication);
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw conflict("EVENT_NOT_OPEN", "This event isn't taking place.");
        }
        String code = request.code().strip().toLowerCase(Locale.ROOT); // ticket codes are lowercase hex
        boolean now = registrationRepository.checkIn(event.getId(), code, Instant.now()) == 1;
        Registration ticket = registrationRepository.findTicket(event.getId(), code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (now) {
            auditService.record("TICKET_CHECKED_IN", event.getAsbl(), "Registration", ticket.getId(),
                    Map.of("ticket", ticket.getTicketCategory().getLabel()));
        } else if (ticket.getStatus() != RegistrationStatus.ATTENDED) {
            throw conflict("TICKET_NOT_VALID", "This ticket isn't valid any more.");
        }
        return new CheckIn(now ? "CHECKED_IN" : "ALREADY_CHECKED_IN", Attendees.Attendee.of(ticket).name(),
                ticket.getTicketCategory().getLabel(), ticket.getCheckinAt());
    }

    private static ErrorResponseException conflict(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }

    private List<Attendees.Attendee> attendeesOf(Event event) {
        return registrationRepository.findAttendees(event.getId()).stream().map(Attendees.Attendee::of).toList();
    }

    // Not a member → 403; a member without the role → 403; an event of another association → 404 (IDOR).
    private Event eventFor(String slug, Long eventId, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String role = membershipService.roleOf(user, asbl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return eventService.findEvent(asbl, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
