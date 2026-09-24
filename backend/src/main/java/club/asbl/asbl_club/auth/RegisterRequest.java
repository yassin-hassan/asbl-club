package club.asbl.asbl_club.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Same rules as the server-rendered sign-up form. Passwords: 8 to 100 characters, no composition rules
// (length matters more than forcing symbols); the upper bound keeps Argon2 hashing affordable.
record RegisterRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password) {
}
