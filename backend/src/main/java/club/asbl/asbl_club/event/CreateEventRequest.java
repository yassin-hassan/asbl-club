package club.asbl.asbl_club.event;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

// startsAt is an exact moment (ISO 8601 with an offset, e.g. 2026-12-01T19:00:00Z): the browser converts what the
// user typed from its own time zone, so the server's time zone never matters.
record CreateEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull Instant startsAt,
        @Size(max = 255) String location,
        @NotBlank @Pattern(regexp = "PUBLIC|MEMBERS") String visibility,
        @Schema(description = "Until how many days before the start buyers may cancel their ticket and be refunded; "
                + "0: not refundable. Optional: 0 when creating, unchanged when editing. Once a ticket is sold, it "
                + "can only grow.")
        @Min(0) @Max(365) Integer cancellationDays) {
}
