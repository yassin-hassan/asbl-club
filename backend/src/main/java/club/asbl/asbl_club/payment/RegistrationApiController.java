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
import club.asbl.asbl_club.payment.Registrations.Checkout;
import club.asbl.asbl_club.payment.Registrations.Mine;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

    private static final Logger log = LoggerFactory.getLogger(RegistrationApiController.class);

    private final ReservationService reservationService;
    private final RegistrationRepository registrationRepository;
    private final PaymentService paymentService;
    private final StripeProperties stripeProperties;
    private final EventService eventService;
    private final AsblService asblService;
    private final MembershipService membershipService;
    private final UserService userService;

    RegistrationApiController(ReservationService reservationService, RegistrationRepository registrationRepository,
            PaymentService paymentService, StripeProperties stripeProperties, EventService eventService,
            AsblService asblService, MembershipService membershipService, UserService userService) {
        this.reservationService = reservationService;
        this.registrationRepository = registrationRepository;
        this.paymentService = paymentService;
        this.stripeProperties = stripeProperties;
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
        Registration registration;
        try {
            registration = reservationService.reserve(event, request.ticketCategoryId(), user);
        } catch (TicketNotInEventException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (TicketSoldOutException e) {
            throw conflict("SOLD_OUT", "Sold out.");
        }
        Mine booked = mine(own(registration.getId(), user));
        return ResponseEntity.created(URI.create("/api/v1/registrations/" + booked.id())).body(booked);
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
    @ApiResponse(responseCode = "409", description = "Nothing to pay, or the association can't receive payments yet",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "502", description = "The payment provider couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/registrations/{id}/checkout")
    Checkout checkout(@PathVariable Long id, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Registration registration = own(id, user);
        if (registration.getStatus() != RegistrationStatus.RESERVED) {
            throw conflict("NOTHING_TO_PAY", "This booking has nothing left to pay.");
        }
        Asbl asbl = registration.getEvent().getAsbl();
        if (asbl.getStripeAccountId() == null) {
            throw conflict("PAYMENTS_DISABLED", "This association can't receive payments yet.");
        }
        try {
            PaymentInitiation initiation =
                    paymentService.initiate(registration, asbl, user.getName(), user.getEmail(), user);
            return new Checkout(stripeProperties.publishableKey(), asbl.getStripeAccountId(),
                    initiation.clientSecret(), registration.getAmount(), registration.getCurrency());
        } catch (StripeException e) {
            log.warn("Stripe refused or failed to start a payment for registration {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider couldn't be reached.");
        }
    }

    // Several different conflicts share status 409: a stable "code" tells clients which one, independent of the
    // human-readable (and changeable) detail text.
    private static ErrorResponseException conflict(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }

    // Someone else's booking is "not found": IDs are sequential, so without this check anyone could open (and
    // start paying for) another person's booking by changing the number.
    private Registration own(Long id, User user) {
        return registrationRepository.findByIdWithEventAndAsbl(id)
                .filter(r -> r.getUser() != null && r.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static Mine mine(Registration r) {
        return new Mine(r.getId(), r.getStatus().name(), r.getAmount(), r.getCurrency(), r.getEvent().getId(),
                r.getEvent().getTitle(), r.getTicketCategory().getLabel(), r.getEvent().getAsbl().getSlug());
    }
}
