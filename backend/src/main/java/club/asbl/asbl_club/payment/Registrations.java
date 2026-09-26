package club.asbl.asbl_club.payment;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

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

    // One of my bookings, as the "My bookings" page lists them.
    @Schema(name = "MyBooking")
    public record Booking(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED,
                    allowableValues = {"RESERVED", "PAID", "CONFIRMED", "ATTENDED", "CANCELLED", "REFUNDED", "EXPIRED"})
            String status,
            @Schema(requiredMode = REQUIRED, description = "Amount in euros") BigDecimal amount,
            @Schema(requiredMode = REQUIRED) String currency,
            @Schema(requiredMode = REQUIRED) Long eventId,
            @Schema(requiredMode = REQUIRED) String eventTitle,
            @Schema(requiredMode = REQUIRED) Instant startsAt,
            String location,
            @Schema(requiredMode = REQUIRED) String asblName,
            @Schema(requiredMode = REQUIRED) String asblSlug,
            @Schema(requiredMode = REQUIRED) String ticketLabel,
            @Schema(description = "The code in the ticket's QR code; present once the booking is paid. Whoever has it "
                    + "gets in, so it's only ever shown to the booking's owner.")
            String ticketCode,
            Instant checkedInAt) {
    }

    // A ticket code typed or scanned at the door (a handheld scanner types it like a keyboard).
    public record CheckInRequest(@NotBlank @Size(max = 64) String code) {
    }

    @Schema(name = "CheckIn")
    public record CheckIn(
            @Schema(requiredMode = REQUIRED, allowableValues = {"CHECKED_IN", "ALREADY_CHECKED_IN"},
                    description = "ALREADY_CHECKED_IN: this ticket was used before (at checkedInAt): maybe a copy")
            String outcome,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String ticketLabel,
            @Schema(requiredMode = REQUIRED) Instant checkedInAt) {
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
