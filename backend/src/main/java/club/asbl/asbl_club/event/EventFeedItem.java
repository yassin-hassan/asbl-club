package club.asbl.asbl_club.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// description is optional: events may have none.
public record EventFeedItem(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String title,
        String description,
        @Schema(requiredMode = REQUIRED) Instant startsAt) {
}
