package club.asbl.asbl_club.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// API shapes for managing an association's events (members read, administrators change).
public final class ManagedEvents {

    private ManagedEvents() {
    }

    @Schema(name = "ManagedEventList")
    public record EventList(
            @Schema(requiredMode = REQUIRED) String slug,
            @Schema(requiredMode = REQUIRED) String denomination,
            @Schema(requiredMode = REQUIRED, description = "Whether the viewer may create and change events") boolean canManage,
            @Schema(requiredMode = REQUIRED) List<Item> events) {
    }

    @Schema(name = "ManagedEventItem")
    public record Item(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) String title,
            @Schema(requiredMode = REQUIRED) Instant startsAt,
            @Schema(requiredMode = REQUIRED, allowableValues = {"DRAFT", "PUBLISHED", "CANCELLED", "ENDED"}) String status,
            @Schema(requiredMode = REQUIRED, allowableValues = {"PUBLIC", "MEMBERS"}) String visibility) {
    }

    @Schema(name = "ManagedEvent")
    public record Detail(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) String title,
            String description,
            @Schema(requiredMode = REQUIRED) Instant startsAt,
            String location,
            @Schema(requiredMode = REQUIRED, allowableValues = {"DRAFT", "PUBLISHED", "CANCELLED", "ENDED"}) String status,
            @Schema(requiredMode = REQUIRED, allowableValues = {"PUBLIC", "MEMBERS"}) String visibility,
            @Schema(requiredMode = REQUIRED, description = "Until how many days before the start buyers may cancel; 0: never")
            int cancellationDays,
            @Schema(requiredMode = REQUIRED) boolean canManage,
            @Schema(requiredMode = REQUIRED, description = "Administrators and treasurers see who booked")
            boolean canSeeAttendees,
            @Schema(requiredMode = REQUIRED) List<Ticket> tickets) {
    }

    @Schema(name = "ManagedTicket")
    public record Ticket(
            @Schema(requiredMode = REQUIRED) Long id,
            @Schema(requiredMode = REQUIRED) String label,
            @Schema(requiredMode = REQUIRED, description = "Price in euros") BigDecimal price,
            @Schema(requiredMode = REQUIRED) int totalSeats,
            @Schema(requiredMode = REQUIRED, description = "Seats taken: paid, or held while being paid")
            int soldSeats,
            @Schema(description = "Of the seats taken, those held by bookings still being paid (freed if not paid in "
                    + "time). Only for administrators and treasurers: the other members see whether seats are left")
            Integer pendingSeats) {
    }
}
