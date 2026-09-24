package club.asbl.asbl_club.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

// Every component marked REQUIRED is always present: the generated TypeScript types make it non-optional.
public record AsblResource(
        @Schema(requiredMode = REQUIRED) String slug,
        @Schema(requiredMode = REQUIRED) String denomination) {
}
