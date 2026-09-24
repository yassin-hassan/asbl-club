package club.asbl.asbl_club.auth;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

record TokenResponse(
        @Schema(requiredMode = REQUIRED) String accessToken,
        @Schema(requiredMode = REQUIRED) String tokenType,
        @Schema(requiredMode = REQUIRED) long expiresIn) {
}
