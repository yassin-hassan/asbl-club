package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.user.EmailAlreadyUsedException;
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
import java.time.Duration;
import java.util.Map;
import org.springframework.context.MessageSource;
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
    private final MessageSource messageSource;
    private final WebAuthenticationDetailsSource detailsSource = new WebAuthenticationDetailsSource();

    AuthController(AuthenticationManager authenticationManager, TokenService tokenService, UserService userService,
            RefreshTokenService refreshTokenService, MessageSource messageSource) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.messageSource = messageSource;
    }

    @Operation(operationId = "login", summary = "Log in with email and password, get a short-lived access token "
            + "(body) and a long-lived refresh token (HttpOnly cookie)")
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return logInAndIssueTokens(request.email(), request.password(), httpRequest, HttpStatus.OK);
    }

    // No email verification yet (the app can't send email), so a taken address is reported (409) rather than
    // hidden behind "check your inbox"; the rate limiter keeps this from being used to test many addresses.
    @Operation(operationId = "register", summary = "Create an account and log straight in (same tokens as login)")
    @ApiResponse(responseCode = "201", description = "Account created; logged in",
            content = @Content(schema = @Schema(implementation = TokenResponse.class)))
    @ApiResponse(responseCode = "409", description = "An account with this email already exists",
            content = @Content(mediaType = "application/problem+json"))
    @PostMapping("/register")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        try {
            userService.register(request.name(), request.email(), request.password());
        } catch (EmailAlreadyUsedException e) {
            String message = messageSource.getMessage("email.duplicate", null, LocaleContextHolder.getLocale());
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, message);
            problem.setProperty("errors", Map.of("email", message));
            throw new ErrorResponseException(HttpStatus.CONFLICT, problem, e);
        }
        // Log in through the same password check as the login endpoint: same rules, same audit trail.
        return logInAndIssueTokens(request.email(), request.password(), httpRequest, HttpStatus.CREATED);
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
        TokenResponse accessToken = tokenService.issueAccessToken(user, authentication.getAuthorities());
        String refreshToken = refreshTokenService.issue(user);
        return ResponseEntity.status(successStatus)
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
