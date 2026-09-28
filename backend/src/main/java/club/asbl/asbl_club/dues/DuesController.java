package club.asbl.asbl_club.dues;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.dues.DuesService.AlreadyPaidException;
import club.asbl.asbl_club.dues.DuesService.NoDuesException;
import club.asbl.asbl_club.dues.DuesService.NotAMemberException;
import club.asbl.asbl_club.dues.DuesService.PaymentsDisabledException;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.payment.Registrations.Checkout;
import club.asbl.asbl_club.payment.StripeProperties;
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
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// Membership dues: administrators set the yearly fee; members see what they owe and pay it with the same Stripe
// form as tickets.
@RestController
@Tag(name = "Dues", description = "Membership dues (cotisations)")
class DuesController {

    private static final Logger log = LoggerFactory.getLogger(DuesController.class);

    private final DuesService duesService;
    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;
    private final StripeProperties stripeProperties;

    DuesController(DuesService duesService, AsblService asblService, UserService userService,
            MembershipService membershipService, StripeProperties stripeProperties) {
        this.duesService = duesService;
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
        this.stripeProperties = stripeProperties;
    }

    @Schema(name = "DuesSettings")
    record Settings(
            @Schema(description = "The yearly fee in euros; absent when the association doesn't collect dues")
            BigDecimal annualFee,
            @Schema(requiredMode = REQUIRED, description = "The year dues are currently paid for") int year,
            @Schema(requiredMode = REQUIRED, description = "Whether the association can receive payments (Stripe)")
            boolean paymentsEnabled) {
    }

    @Schema(name = "DuesFeeRequest")
    record FeeRequest(
            @Schema(description = "The yearly fee in euros; absent or null to stop collecting dues")
            @DecimalMin("1.00") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2) BigDecimal annualFee) {
    }

    @Schema(name = "MyDues")
    record MyDues(@Schema(requiredMode = REQUIRED) String slug, @Schema(requiredMode = REQUIRED) String association,
            @Schema(requiredMode = REQUIRED) int year,
            @Schema(requiredMode = REQUIRED, description = "Amount in euros") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) boolean paid,
            @Schema(description = "When it was paid; absent while it isn't") Instant paidAt) {
    }

    @Operation(operationId = "getDuesSettings", summary = "The association's yearly fee (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/asbls/{slug}/manage/dues")
    Settings settings(@PathVariable String slug, Authentication authentication) {
        Asbl asbl = asAdmin(slug, userService.getAuthenticated(authentication));
        return new Settings(asbl.getAnnualFee(), duesService.currentYear(), asbl.getStripeAccountId() != null);
    }

    @Operation(operationId = "setDuesFee", summary = "Set or remove the yearly fee (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "204", description = "Saved")
    @ApiResponse(responseCode = "409", description = "The association can't receive payments yet (PAYMENTS_DISABLED)",
            content = @Content(mediaType = "application/problem+json"))
    @PutMapping("/api/v1/asbls/{slug}/manage/dues")
    ResponseEntity<Void> setFee(@PathVariable String slug, @Valid @RequestBody FeeRequest request,
            Authentication authentication) {
        Asbl asbl = asAdmin(slug, userService.getAuthenticated(authentication));
        try {
            duesService.setFee(asbl, request.annualFee());
        } catch (PaymentsDisabledException e) {
            throw paymentsDisabled();
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(operationId = "listMyDues", summary = "This year's dues in each of my associations that collects them",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/me/dues")
    List<MyDues> mine(Authentication authentication) {
        return duesService.duesOf(userService.getAuthenticated(authentication)).stream()
                .map(d -> new MyDues(d.asbl().getSlug(), d.asbl().getDenomination(), d.year(), d.amount(),
                        d.paidAt().isPresent(), d.paidAt().orElse(null)))
                .toList();
    }

    @Operation(operationId = "startDuesCheckout", summary = "Start paying this year's dues: what the browser needs "
            + "to show Stripe's payment form", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Ready to pay",
            content = @Content(schema = @Schema(implementation = Checkout.class)))
    @ApiResponse(responseCode = "409", description = "No dues to pay (NO_DUES), already paid (ALREADY_PAID), or the "
            + "association can't receive payments yet (PAYMENTS_DISABLED)",
            content = @Content(mediaType = "application/problem+json"))
    @ApiResponse(responseCode = "502", description = "The payment provider couldn't be reached",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/api/v1/asbls/{slug}/dues/checkout")
    Checkout checkout(@PathVariable String slug, Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            DuesService.Started started = duesService.startPayment(asbl, user);
            return new Checkout(stripeProperties.publishableKey(), asbl.getStripeAccountId(), started.clientSecret(),
                    started.amount(), "EUR");
        } catch (NotAMemberException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (NoDuesException e) {
            throw problem(HttpStatus.CONFLICT, "NO_DUES", "This association doesn't collect dues.");
        } catch (AlreadyPaidException e) {
            throw problem(HttpStatus.CONFLICT, "ALREADY_PAID", "This year's dues are already paid.");
        } catch (PaymentsDisabledException e) {
            throw paymentsDisabled();
        } catch (StripeException e) {
            log.warn("Stripe refused or failed to start a dues payment in {}: {}", slug, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider couldn't be reached.");
        }
    }

    private Asbl asAdmin(String slug, User user) {
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return asbl;
    }

    private static ErrorResponseException paymentsDisabled() {
        return problem(HttpStatus.CONFLICT, "PAYMENTS_DISABLED", "This association can't receive payments yet.");
    }

    private static ErrorResponseException problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(status, problem, null);
    }
}
