package club.asbl.asbl_club.audit;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

// One page of an audit journal, newest first.
@Schema(name = "AuditJournal")
public record AuditJournal(
        @Schema(description = "The association's name; absent in the platform journal") String denomination,
        @Schema(requiredMode = REQUIRED) List<Entry> entries,
        @Schema(requiredMode = REQUIRED, description = "0-based") int page,
        @Schema(requiredMode = REQUIRED) int totalPages,
        @Schema(requiredMode = REQUIRED) long totalEntries) {

    @Schema(name = "AuditEntry")
    public record Entry(
            @Schema(requiredMode = REQUIRED) Instant createdAt,
            @Schema(requiredMode = REQUIRED, example = "EVENT_PUBLISHED") String action,
            @Schema(description = "Absent for actions done by the system (e.g. a Stripe webhook)") String actorEmail,
            @Schema(description = "Association concerned, if any") String asbl,
            String entityType,
            Long entityId,
            @Schema(description = "Platform journal only: an IP address is personal data an association doesn't need")
            String ip) {
    }
}
