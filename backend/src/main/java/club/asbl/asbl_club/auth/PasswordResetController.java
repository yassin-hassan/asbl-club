package club.asbl.asbl_club.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "Authentication", description = "Exchange credentials for tokens")
class PasswordResetController {

    private final PasswordResetService passwordResetService;

    PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    record ResetRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    // Same password rules as registration (8 to 100 characters).
    record NewPassword(@NotBlank @Size(max = 100) String token, @NotBlank @Size(min = 8, max = 100) String password) {
    }

    @Operation(operationId = "requestPasswordReset",
            summary = "Email a link to choose a new password (same answer whether or not the account exists)")
    @ApiResponse(responseCode = "202", description = "Accepted: if an account exists, an email is on its way")
    @PostMapping
    ResponseEntity<Void> request(@Valid @RequestBody ResetRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @Operation(operationId = "resetPassword", summary = "Choose a new password with the emailed link's token; "
            + "every session of the account is ended")
    @ApiResponse(responseCode = "204", description = "Password changed; log in with the new one")
    @ApiResponse(responseCode = "400", description = "Invalid, expired or already used link (INVALID_RESET_TOKEN), "
            + "or a password that breaks the rules", content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/confirm")
    ResponseEntity<Void> confirm(@Valid @RequestBody NewPassword request) {
        try {
            passwordResetService.resetPassword(request.token(), request.password());
        } catch (InvalidResetTokenException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "This link is invalid, expired or already used.");
            problem.setProperty("code", "INVALID_RESET_TOKEN");
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, e);
        }
        return ResponseEntity.noContent().build();
    }
}
