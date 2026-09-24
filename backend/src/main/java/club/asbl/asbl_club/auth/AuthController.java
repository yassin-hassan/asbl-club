package club.asbl.asbl_club.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Exchange credentials for tokens")
class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @Operation(summary = "Log in with email and password, get a short-lived access token")
    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UsernamePasswordAuthenticationToken attempt =
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());
        // Carries the client IP, which the login audit log records.
        attempt.setDetails(detailsSource.buildDetails(httpRequest));
        try {
            Authentication authentication = authenticationManager.authenticate(attempt);
            return tokenService.issueAccessToken(authentication);
        } catch (AuthenticationException e) {
            // One answer for "unknown email" and "wrong password", so the endpoint can't be used
            // to find out which emails have an account.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
