package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
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

    static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    AuthController(AuthenticationManager authenticationManager, TokenService tokenService, UserService userService,
            RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
    }

    @Operation(summary = "Log in with email and password, get a short-lived access token "
            + "(body) and a long-lived refresh token (HttpOnly cookie)")
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        UsernamePasswordAuthenticationToken attempt =
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password());
        // Carries the client IP, which the login audit log records.
        attempt.setDetails(detailsSource.buildDetails(httpRequest));
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(attempt);
        } catch (AuthenticationException e) {
            // One answer for "unknown email" and "wrong password", so the endpoint can't be used
            // to find out which emails have an account.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        // The password check only gives us the email; the token needs the user's public ID.
        User user = userService.getByEmail(authentication.getName());
        TokenResponse accessToken = tokenService.issueAccessToken(user, authentication.getAuthorities());
        String refreshToken = refreshTokenService.issue(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString())
                .body(accessToken);
    }

    // HttpOnly: page JavaScript (and so an XSS payload) can't read it.
    // Secure: only sent over HTTPS (browsers make an exception for http://localhost).
    // SameSite=Strict: never sent on requests started by another site.
    // Path: only sent to /api/v1/auth/*, not with every API call.
    private static ResponseCookie refreshTokenCookie(String value) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(RefreshTokenService.REFRESH_TOKEN_TTL)
                .build();
    }
}
