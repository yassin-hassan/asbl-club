package club.asbl.asbl_club.auth;

import jakarta.validation.constraints.NotBlank;

record LoginRequest(
        @NotBlank String email,
        @NotBlank String password) {
}
