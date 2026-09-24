package club.asbl.asbl_club.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

public record SeatAvailability(
        @Schema(requiredMode = REQUIRED) Long categoryId,
        @Schema(requiredMode = REQUIRED) int remaining) {
}
