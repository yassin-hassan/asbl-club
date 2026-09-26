package club.asbl.asbl_club.payment;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Who booked an event, for its administrators and treasurers: every booking, whatever its status, so paid ones on a
// cancelled event (to refund) and abandoned ones are visible too.
@Schema(name = "Attendees")
record Attendees(
        @Schema(requiredMode = REQUIRED) String eventTitle,
        @Schema(requiredMode = REQUIRED) List<Attendee> attendees) {

    @Schema(name = "Attendee")
    record Attendee(
            @Schema(requiredMode = REQUIRED) Long bookingId,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String email,
            @Schema(requiredMode = REQUIRED) String ticket,
            @Schema(requiredMode = REQUIRED,
                    allowableValues = {"RESERVED", "PAID", "CONFIRMED", "ATTENDED", "CANCELLED", "REFUNDED", "EXPIRED"})
            String status,
            @Schema(requiredMode = REQUIRED) BigDecimal amount,
            @Schema(requiredMode = REQUIRED) String currency,
            @Schema(requiredMode = REQUIRED) Instant bookedAt) {

        static Attendee of(Registration r) {
            // A booking belongs to a member (a closed account stays linked, anonymised) or, later, to a guest.
            String name = r.getUser() != null ? r.getUser().getName() : "";
            String email = r.getUser() != null ? r.getUser().getEmail() : r.getGuestEmail();
            return new Attendee(r.getId(), name, email, r.getTicketCategory().getLabel(), r.getStatus().name(),
                    r.getAmount(), r.getCurrency(), r.getRegisteredAt());
        }
    }
}
