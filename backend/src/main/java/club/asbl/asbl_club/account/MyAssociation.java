package club.asbl.asbl_club.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

// An association the current user belongs to (or asked to join), their role and membership status. Identified by its public slug.
public record MyAssociation(
        @Schema(requiredMode = REQUIRED) String slug,
        @Schema(requiredMode = REQUIRED) String denomination,
        @Schema(requiredMode = REQUIRED, allowableValues = {"ADMIN", "TREASURER", "VIEWER", "MEMBER"}) String role,
        @Schema(requiredMode = REQUIRED, description = "PENDING while a join request awaits an administrator",
                allowableValues = {"PENDING", "ACTIVE", "EXCLUDED", "LEFT"}) String status) {
}
