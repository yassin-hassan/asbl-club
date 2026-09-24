package club.asbl.asbl_club.payment;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// API shapes for booking a ticket and paying for it.
public final class Registrations {

    private Registrations() {
    }

    public record BookRequest(@NotNull Long ticketCategoryId) {
    }

    @Schema(name = "MyRegistration")
    public record Mine(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED,
                    allowableValues = {"RESERVED", "PAID", "CONFIRMED", "ATTENDED", "CANCELLED", "REFUNDED", "EXPIRED"})
            String status,
            @Schema(requiredMode = REQUIRED, description = "Amount in euros") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) String currency,
            @Schema(requiredMode = REQUIRED) Long eventId,
            @Schema(requiredMode = REQUIRED) String eventTitle,
            @Schema(requiredMode = REQUIRED) String ticketLabel,
            @Schema(requiredMode = REQUIRED) String asblSlug) {
    }

    // What the browser needs to show Stripe's payment form for this booking. The client secret authorises
    // confirming this one payment only; it's shown to the booking's owner only.
    @Schema(name = "Checkout")
    public record Checkout(
            @Schema(requiredMode = REQUIRED) String publishableKey,
            @Schema(requiredMode = REQUIRED, description = "The association's Stripe account (payments go straight to it)")
            String stripeAccount,
            @Schema(requiredMode = REQUIRED) String clientSecret,
            @Schema(requiredMode = REQUIRED, description = "Amount in euros") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) String currency) {
    }
}
