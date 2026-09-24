package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.asbl.PaymentSetup.OnboardingLink;
import club.asbl.asbl_club.asbl.PaymentSetup.Status;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.exception.StripeException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

// Connecting an association to Stripe so it can receive payments (Stripe Connect "Express" onboarding).
// Administrators only: this hands the association's money flow to a Stripe account.
@RestController
@RequestMapping("/api/v1/asbls/{slug}/manage/payments")
@Tag(name = "Payment setup", description = "Connecting an association to Stripe (administrators)")
class PaymentSetupController {

    private static final Logger log = LoggerFactory.getLogger(PaymentSetupController.class);

    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;
    private final StripeConnectService stripeConnectService;

    PaymentSetupController(AsblService asblService, UserService userService, MembershipService membershipService,
            StripeConnectService stripeConnectService) {
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
        this.stripeConnectService = stripeConnectService;
    }

    @Operation(operationId = "getPaymentSetup", summary = "Whether the association can receive payments",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = Status.class)))
    @ApiResponse(responseCode = "502", description = "Stripe couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping
    Status status(@PathVariable String slug, Authentication authentication) {
        Asbl asbl = asAdmin(slug, authentication);
        try {
            return new Status(asbl.getSlug(), asbl.getDenomination(), stripeConnectService.status(asbl).name());
        } catch (StripeException e) {
            throw stripeUnavailable(slug, e);
        }
    }

    // POST: the first call creates the association's Stripe account. The link is single use and short-lived,
    // so the browser asks for a fresh one each time and goes there straight away.
    // Stripe sends the administrator back to the Angular payments page: plainly when done, with ?resume when
    // the link had expired (the page then asks for a new one).
    @Operation(operationId = "startStripeOnboarding", summary = "Get a Stripe onboarding link for the association",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Go to this address now",
            content = @Content(schema = @Schema(implementation = OnboardingLink.class)))
    @ApiResponse(responseCode = "502", description = "Stripe couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/onboarding")
    OnboardingLink startOnboarding(@PathVariable String slug, Authentication authentication) {
        Asbl asbl = asAdmin(slug, authentication);
        String page = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/asbls/{slug}/manage/payments").buildAndExpand(asbl.getSlug()).toUriString();
        try {
            return new OnboardingLink(stripeConnectService.startOnboarding(asbl, page + "?resume", page));
        } catch (StripeException e) {
            throw stripeUnavailable(slug, e);
        }
    }

    // Non-members and plain members get 403, as elsewhere in the back office.
    private Asbl asAdmin(String slug, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return asbl;
    }

    private static ResponseStatusException stripeUnavailable(String slug, StripeException e) {
        log.warn("Stripe Connect call failed for association {}: {}", slug, e.getMessage());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider couldn't be reached.");
    }
}
