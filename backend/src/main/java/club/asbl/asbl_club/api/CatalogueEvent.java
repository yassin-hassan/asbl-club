package club.asbl.asbl_club.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// One event of the platform's catalogue, with what a list shows: when, where, and which association.
public record CatalogueEvent(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String title,
        @Schema(requiredMode = REQUIRED) Instant startsAt,
        String location,
        @Schema(requiredMode = REQUIRED) AsblResource asbl) {
}
