package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Collection;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.ErrorResponseException;
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
    private final EmailVerificationService emailVerificationService;
    private final UserDetailsService userDetailsService;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    AuthController(AuthenticationManager authenticationManager, TokenService tokenService, UserService userService,
            RefreshTokenService refreshTokenService, EmailVerificationService emailVerificationService,
            UserDetailsService userDetailsService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.userDetailsService = userDetailsService;
    }

    @Operation(operationId = "login", summary = "Log in with email and password, get a short-lived access token "
            + "(body) and a long-lived refresh token (HttpOnly cookie)")
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return logInAndIssueTokens(request.email(), request.password(), httpRequest, HttpStatus.OK);
    }

    // Always the same answer, "check your inbox", so this can't be used to find out who has an account: the email
    // says the rest (a link to confirm, or "you already have an account").
    @Operation(operationId = "register", summary = "Sign up: an email follows, with a link to confirm the address "
            + "(the same answer whether or not the address already has an account)")
    @ApiResponse(responseCode = "202", description = "Accepted: check your inbox")
    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        emailVerificationService.signUp(request.name(), request.email(), request.password());
        return ResponseEntity.accepted().build();
    }

    record VerifyRequest(@NotBlank @Size(max = 100) String token) {
    }

    record ResendRequest(@NotBlank @Email @Size(max = 255) String email) {
    }

    // The emailed link's token confirms the address and logs the person in (same tokens as a login).
    @Operation(operationId = "verifyEmail", summary = "Confirm the email address with the emailed link's token, "
            + "and log in")
    @ApiResponse(responseCode = "200", description = "Confirmed and logged in",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid, expired or already used link (INVALID_VERIFICATION_TOKEN)",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/verify-email")
    ResponseEntity<TokenResponse> verifyEmail(@Valid @RequestBody VerifyRequest request) {
        User user;
        try {
            user = emailVerificationService.verify(request.token());
        } catch (InvalidVerificationTokenException e) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                    "This link is invalid, expired or already used.");
            problem.setProperty("code", "INVALID_VERIFICATION_TOKEN");
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, e);
        }
        // The roles as a login would load them.
        var authorities = userDetailsService.loadUserByUsername(user.getEmail()).getAuthorities();
        userService.rememberLanguage(user, LocaleContextHolder.getLocale());
        return issueTokens(user, authorities, HttpStatus.OK);
    }

    @Operation(operationId = "resendVerificationEmail",
            summary = "Send the confirmation link again (same answer whatever the address)")
    @ApiResponse(responseCode = "202", description = "Accepted: if an account waits for confirmation, a link follows")
    @PostMapping("/verify-email/resend")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendRequest request) {
        emailVerificationService.resend(request.email());
        return ResponseEntity.accepted().build();
    }

    private ResponseEntity<TokenResponse> logInAndIssueTokens(String email, String password,
            HttpServletRequest httpRequest, HttpStatus successStatus) {
        UsernamePasswordAuthenticationToken attempt = UsernamePasswordAuthenticationToken.unauthenticated(email, password);
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
        // Checked only now, after the right password: someone without it learns nothing about the account.
        if (user.getEmailVerifiedAt() == null) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                    "Confirm your email address first: use the link we sent you.");
            problem.setProperty("code", "EMAIL_NOT_VERIFIED");
            throw new ErrorResponseException(HttpStatus.FORBIDDEN, problem, null);
        }
        userService.rememberLanguage(user, LocaleContextHolder.getLocale());
        return issueTokens(user, authentication.getAuthorities(), successStatus);
    }

    private ResponseEntity<TokenResponse> issueTokens(User user,
            Collection<? extends GrantedAuthority> authorities,
            HttpStatus status) {
        TokenResponse accessToken = tokenService.issueAccessToken(user, authorities);
        String refreshToken = refreshTokenService.issue(user);
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(refreshToken).toString())
                .body(accessToken);
    }

    @Operation(operationId = "refresh", summary = "Exchange the refresh token cookie for a new access token and a new refresh token")
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        if (refreshToken == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        RefreshTokenService.Rotation rotation;
        try {
            rotation = refreshTokenService.rotate(refreshToken);
        } catch (InvalidRefreshTokenException e) {
            // Tell the browser to drop the dead cookie, so it stops sending it; the 401 body comes from the
            // API error handler like every other error.
            httpResponse.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        TokenResponse accessToken = tokenService.issueAccessToken(rotation.user(), rotation.authorities());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(rotation.refreshToken()).toString())
                .body(accessToken);
    }

    @Operation(operationId = "logout", summary = "Log out: revoke this login session's refresh tokens and clear the cookie")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null) {
            refreshTokenService.revokeSession(refreshToken);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString())
                .build();
    }

    // HttpOnly: page JavaScript (and so an XSS payload) can't read it.
    // Secure: only sent over HTTPS (browsers make an exception for http://localhost).
    // SameSite=Strict: never sent on requests started by another site.
    // Path: only sent to /api/v1/auth/*, not with every API call.
    private static ResponseCookie refreshTokenCookie(String value) {
        return refreshTokenCookie(value, RefreshTokenService.REFRESH_TOKEN_TTL);
    }

    // Same name and path as the real cookie, empty value, Max-Age 0: the browser deletes it.
    private static ResponseCookie expiredRefreshTokenCookie() {
        return refreshTokenCookie("", Duration.ZERO);
    }

    private static ResponseCookie refreshTokenCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
