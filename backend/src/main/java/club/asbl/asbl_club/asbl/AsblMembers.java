package club.asbl.asbl_club.asbl;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

// An association's member page: who's in it, and what the viewer may do there.
public record AsblMembers(
        @Schema(requiredMode = REQUIRED) String slug,
        @Schema(requiredMode = REQUIRED) String denomination,
        @Schema(requiredMode = REQUIRED, description = "The viewer's role in this association") String myRole,
        @Schema(requiredMode = REQUIRED, description = "Whether payments can be received (Stripe connected)")
        boolean paymentsEnabled,
        @Schema(requiredMode = REQUIRED) List<Member> members) {

    @Schema(name = "AsblMember")
    public record Member(
            @Schema(requiredMode = REQUIRED, description = "The person's public ID (used to approve or decline a request)")
            UUID id,
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) String email,
            @Schema(requiredMode = REQUIRED, allowableValues = {"ADMIN", "TREASURER", "VIEWER", "MEMBER"}) String role,
            @Schema(requiredMode = REQUIRED, allowableValues = {"PENDING", "ACTIVE", "EXCLUDED", "LEFT"}) String status) {
    }
}
