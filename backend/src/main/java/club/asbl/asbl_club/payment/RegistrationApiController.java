package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.EventStatus;
import club.asbl.asbl_club.event.TicketNotInEventException;
import club.asbl.asbl_club.event.TicketSoldOutException;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.payment.Registrations.BookRequest;
import club.asbl.asbl_club.payment.Registrations.Booking;
import club.asbl.asbl_club.payment.Registrations.Checkout;
import club.asbl.asbl_club.payment.Registrations.Mine;
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
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Booking a ticket and paying for it. Card details never reach this server: the browser sends them straight to
// Stripe; a booking becomes PAID only through Stripe's signature-verified webhook, never because the browser
// came back to the "complete" page.
@RestController
@Tag(name = "Registrations", description = "Booking tickets and paying for them")
class RegistrationApiController {

    private final ReservationService reservationService;
    private final RegistrationRepository registrationRepository;
    private final BookingCheckout bookingCheckout;
    private final EventService eventService;
    private final AsblService asblService;
    private final MembershipService membershipService;
    private final UserService userService;

    RegistrationApiController(ReservationService reservationService, RegistrationRepository registrationRepository,
            BookingCheckout bookingCheckout, EventService eventService, AsblService asblService,
            MembershipService membershipService, UserService userService) {
        this.reservationService = reservationService;
        this.registrationRepository = registrationRepository;
        this.bookingCheckout = bookingCheckout;
        this.eventService = eventService;
        this.asblService = asblService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    // Members book a seat on one of the association's published events. The seat is taken at once (an atomic
    // "take one if any left" in the database), so two people can't get the last one.
    @Operation(operationId = "bookTicket", summary = "Book a seat (members of the association)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Booked; pay next",
            content = @Content(schema = @Schema(implementation = Mine.class)))
    @ApiResponse(responseCode = "409", description = "Sold out, or the event isn't open for booking",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/asbls/{slug}/manage/events/{eventId}/registrations")
    ResponseEntity<Mine> book(@PathVariable String slug, @PathVariable Long eventId,
            @Valid @RequestBody BookRequest request, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (membershipService.roleOf(user, asbl).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        Event event = eventService.findEvent(asbl, eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw conflict("NOT_BOOKABLE", "This event isn't open for booking.");
        }
        return bookAs(user, event, request.ticketCategoryId());
    }

    // Anyone with an account books a seat on a public event, from its public page (no membership needed). Events
    // for members only are booked from the association's space, above.
    @Operation(operationId = "bookPublicEvent", summary = "Book a seat on a public event (any account)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "201", description = "Booked; pay next",
            content = @Content(schema = @Schema(implementation = Mine.class)))
    @ApiResponse(responseCode = "409", description = "Sold out (SOLD_OUT), or the association can't receive payments yet (PAYMENTS_DISABLED)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/events/{eventId}/registrations")
    ResponseEntity<Mine> bookPublic(@PathVariable Long eventId, @Valid @RequestBody BookRequest request,
            Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Event event = eventService.findPublicEvent(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BookingCheckout.requirePayments(event.getAsbl());
        return bookAs(user, event, request.ticketCategoryId());
    }

    private ResponseEntity<Mine> bookAs(User user, Event event, Long ticketCategoryId) {
        Registration registration;
        try {
            registration = reservationService.reserve(event, ticketCategoryId, user);
        } catch (TicketNotInEventException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (TicketSoldOutException e) {
            throw conflict("SOLD_OUT", "Sold out.");
        }
        Mine booked = mine(own(registration.getId(), user));
        return ResponseEntity.created(URI.create("/api/v1/registrations/" + booked.id())).body(booked);
    }

    @Operation(operationId = "listMyBookings", summary = "My bookings, soonest event first",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/registrations")
    List<Booking> myBookings(Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        return registrationRepository.findMine(user.getId()).stream().map(RegistrationApiController::booking).toList();
    }

    @Operation(operationId = "getMyRegistration", summary = "One of my bookings and its payment status",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/registrations/{id}")
    Mine get(@PathVariable Long id, Authentication authentication) {
        return mine(own(id, userService.getAuthenticated(authentication)));
    }

    // POST, not GET: it creates (or reuses) a payment attempt at Stripe.
    @Operation(operationId = "startCheckout", summary = "Start (or resume) paying for one of my bookings",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Ready to pay",
            content = @Content(schema = @Schema(implementation = Checkout.class)))
    @ApiResponse(responseCode = "409", description = "Nothing to pay (NOTHING_TO_PAY), not paid in time (BOOKING_EXPIRED), or the association can't receive payments yet (PAYMENTS_DISABLED)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "502", description = "The payment provider couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/registrations/{id}/checkout")
    Checkout checkout(@PathVariable Long id, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        return bookingCheckout.start(own(id, user), user.getName(), user.getEmail(), user);
    }

    // The buyer cancels their paid ticket, within the event's cancellation delay: refunded (the platform keeps its
    // commission), the seat back on sale.
    @Operation(operationId = "cancelMyBooking", summary = "Cancel one of my paid tickets and be refunded",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Cancelled and refunded",
            content = @Content(schema = @Schema(implementation = Booking.class)))
    @ApiResponse(responseCode = "409", description = "The delay has passed or tickets aren't refundable (CANCELLATION_CLOSED), or not a paid unused ticket of an event still on (NOT_CANCELLABLE)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "502", description = "The payment provider couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/registrations/{id}/cancel")
    Booking cancel(@PathVariable Long id, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        bookingCheckout.cancel(own(id, user));
        return booking(own(id, user));
    }

    private static ErrorResponseException conflict(String code, String detail) {
        return BookingCheckout.conflict(code, detail);
    }

    // Someone else's booking is "not found": IDs are sequential, so without this check anyone could open (and
    // start paying for) another person's booking by changing the number.
    private Registration own(Long id, User user) {
        return registrationRepository.findByIdWithEventAndAsbl(id)
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // The ticket code only once paid (or used): before that there's no ticket to show.
    private static Booking booking(Registration r) {
        boolean ticket = r.getStatus() == RegistrationStatus.PAID || r.getStatus() == RegistrationStatus.CONFIRMED
                || r.getStatus() == RegistrationStatus.ATTENDED;
        return new Booking(r.getId(), r.getStatus().name(), r.getAmount(), r.getCurrency(), r.getEvent().getId(),
                r.getEvent().getTitle(), r.getEvent().getStartsAt(), r.getEvent().getLocation(),
                r.getEvent().getAsbl().getDenomination(), r.getEvent().getAsbl().getSlug(),
                r.getTicketCategory().getLabel(), ticket ? r.getQrToken() : null, r.getCheckinAt(),
                r.cancellableUntil(Instant.now()).orElse(null));
    }

    private static Mine mine(Registration r) {
        return new Mine(r.getId(), r.getStatus().name(), r.getAmount(), r.getCurrency(), r.getEvent().getId(),
                r.getEvent().getTitle(), r.getTicketCategory().getLabel(), r.getEvent().getAsbl().getSlug());
    }
}
