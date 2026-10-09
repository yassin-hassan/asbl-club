package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.auth.OneTimeTokens;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.TicketNotInEventException;
import club.asbl.asbl_club.event.TicketSoldOutException;
import club.asbl.asbl_club.payment.Registrations.Checkout;
import club.asbl.asbl_club.payment.Registrations.GuestBookRequest;
import club.asbl.asbl_club.payment.Registrations.GuestBooked;
import club.asbl.asbl_club.payment.Registrations.GuestBooking;
import club.asbl.asbl_club.payment.ReservationService.GuestReservation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Buying a ticket without an account (a "guest"), on a public event, as the analysis describes: a name and an email
// address are enough. There is no login, so the booking is reached through a secret link, emailed at once: whoever
// has it can pay for the booking and see its ticket. The link is 256 random bits and only its hash is stored.
@RestController
@Tag(name = "Guest bookings", description = "Buying a ticket for a public event without an account")
class GuestBookingController {

    private final ReservationService reservationService;
    private final RegistrationRepository registrationRepository;
    private final BookingCheckout bookingCheckout;
    private final EventService eventService;

    GuestBookingController(ReservationService reservationService, RegistrationRepository registrationRepository,
            BookingCheckout bookingCheckout, EventService eventService) {
        this.reservationService = reservationService;
        this.registrationRepository = registrationRepository;
        this.bookingCheckout = bookingCheckout;
        this.eventService = eventService;
    }

    // Public and unauthenticated: it takes a seat and sends an email, so it's rate-limited per IP address
    // (AuthRateLimitFilter), and an unpaid seat goes back on sale when the booking expires.
    @Operation(operationId = "bookAsGuest", summary = "Book a seat on a public event, without an account")
    @ApiResponse(responseCode = "201", description = "Booked; the secret link to pay and to see the ticket is in the "
            + "body and was emailed", content = @Content(schema = @Schema(implementation = GuestBooked.class)))
    @ApiResponse(responseCode = "404", description = "No such public event, or a ticket of another event",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "409", description = "Sold out (SOLD_OUT), or the association can't receive payments yet (PAYMENTS_DISABLED)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/guest-bookings")
    ResponseEntity<GuestBooked> book(@Valid @RequestBody GuestBookRequest request) {
        // Members-only events, drafts and cancelled events aren't public: "not found", as on the public page.
        Event event = eventService.findPublicEvent(request.eventId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BookingCheckout.requirePayments(event.getAsbl());
        GuestReservation reservation;
        try {
            reservation = reservationService.reserveForGuest(event, request.ticketCategoryId(), request.name(),
                    request.email(), request.language());
        } catch (TicketNotInEventException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (TicketSoldOutException e) {
            throw BookingCheckout.conflict("SOLD_OUT", "Sold out.");
        }
        GuestBooking booking = view(byToken(reservation.accessToken()));
        return ResponseEntity.status(HttpStatus.CREATED).body(new GuestBooked(reservation.accessToken(), booking));
    }

    @Operation(operationId = "getGuestBooking", summary = "A guest's booking, through its secret link")
    @ApiResponse(responseCode = "404", description = "Unknown link",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/api/v1/guest-bookings/{token}")
    GuestBooking get(@PathVariable String token) {
        return view(byToken(token));
    }

    // POST, not GET: it creates (or reuses) a payment attempt at Stripe.
    @Operation(operationId = "startGuestCheckout", summary = "Start (or resume) paying for a guest's booking")
    @ApiResponse(responseCode = "200", description = "Ready to pay",
            content = @Content(schema = @Schema(implementation = Checkout.class)))
    @ApiResponse(responseCode = "409", description = "Nothing to pay (NOTHING_TO_PAY), not paid in time (BOOKING_EXPIRED), or the association can't receive payments yet (PAYMENTS_DISABLED)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "502", description = "The payment provider couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/guest-bookings/{token}/checkout")
    Checkout checkout(@PathVariable String token) {
        Registration registration = byToken(token);
        return bookingCheckout.start(registration, registration.getGuestName(), registration.getGuestEmail(), null);
    }

    @Operation(operationId = "cancelGuestBooking", summary = "Cancel a guest's paid ticket and be refunded")
    @ApiResponse(responseCode = "200", description = "Cancelled and refunded",
            content = @Content(schema = @Schema(implementation = GuestBooking.class)))
    @ApiResponse(responseCode = "409", description = "The delay has passed or tickets aren't refundable (CANCELLATION_CLOSED), or not a paid unused ticket of an event still on (NOT_CANCELLABLE)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "502", description = "The payment provider couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/guest-bookings/{token}/cancel")
    GuestBooking cancel(@PathVariable String token) {
        bookingCheckout.cancel(byToken(token));
        return view(byToken(token));
    }

    // An unknown or malformed link is simply "not found" (and a link never opens an account holder's booking).
    private Registration byToken(String token) {
        if (token == null || token.length() > 64) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return registrationRepository.findByAccessTokenHash(OneTimeTokens.hash(token))
                .filter(r -> r.getUser() == null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    // The ticket code only once paid (or used): before that there's no ticket to show.
    private static GuestBooking view(Registration r) {
        boolean ticket = r.getStatus() == RegistrationStatus.PAID || r.getStatus() == RegistrationStatus.CONFIRMED
                || r.getStatus() == RegistrationStatus.ATTENDED;
        Event event = r.getEvent();
        return new GuestBooking(r.getStatus().name(), r.getGuestName(), r.getGuestEmail(), r.getAmount(),
                r.getCurrency(), event.getId(), event.getTitle(), event.getStartsAt(), event.getLocation(),
                event.getAsbl().getDenomination(), r.getTicketCategory().getLabel(), ticket ? r.getQrToken() : null,
                r.getCheckinAt(), r.cancellableUntil(Instant.now()).orElse(null));
    }
}
