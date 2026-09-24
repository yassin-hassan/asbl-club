package club.asbl.asbl_club.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

// A public event with everything its public page shows. A superset of the list item (EventFeedItem):
// callers that only read the list fields keep working. description and location are optional.
public record PublicEvent(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String title,
        String description,
        @Schema(requiredMode = REQUIRED) Instant startsAt,
        String location,
        @Schema(requiredMode = REQUIRED) AsblResource asbl,
        @Schema(requiredMode = REQUIRED) List<PublicTicket> tickets) {
}
