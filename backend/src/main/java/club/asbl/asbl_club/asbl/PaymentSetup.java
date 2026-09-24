package club.asbl.asbl_club.asbl;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

// Whether an association can receive payments, and the link that sets it up.
public final class PaymentSetup {

    private PaymentSetup() {
    }

    @Schema(name = "PaymentSetupStatus")
    public record Status(
            @Schema(requiredMode = REQUIRED) String slug,
            @Schema(requiredMode = REQUIRED) String denomination,
            @Schema(requiredMode = REQUIRED, allowableValues = {"NOT_CONNECTED", "PENDING", "READY"},
                    description = "NOT_CONNECTED: no Stripe account yet; PENDING: onboarding unfinished or under "
                            + "review at Stripe; READY: card payments accepted")
            String status) {
    }

    @Schema(name = "StripeOnboardingLink")
    public record OnboardingLink(
            @Schema(requiredMode = REQUIRED, description = "Stripe-hosted onboarding page; single use, expires quickly")
            String url) {
    }
}
