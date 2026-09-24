package club.asbl.asbl_club.api;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

// A ticket category as the public sees it. Prices are in euros; remaining seats also come live from
// the availability endpoint.
public record PublicTicket(
        @Schema(requiredMode = REQUIRED) Long id,
        @Schema(requiredMode = REQUIRED) String label,
        @Schema(requiredMode = REQUIRED, description = "Price in euros") BigDecimal price,
        @Schema(requiredMode = REQUIRED) int remaining) {
}
