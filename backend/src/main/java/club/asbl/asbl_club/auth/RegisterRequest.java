package club.asbl.asbl_club.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Passwords: 15 to 100 characters, no composition rules (NIST SP 800-63B, 2024: length matters more than forcing
// symbols, and 15 is the minimum when the password is the only factor); the upper bound keeps Argon2 affordable.
record RegisterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 15, max = 100) String password) {
}
