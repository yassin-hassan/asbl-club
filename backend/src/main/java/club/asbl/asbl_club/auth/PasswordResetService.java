package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.email.EmailService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// "Forgot password": an emailed, single-use, short-lived link to choose a new password.
@Service
class PasswordResetService {

    static final Duration VALIDITY = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokens;
    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final String publicUrl;

    PasswordResetService(PasswordResetTokenRepository tokens, UserService userService,
            RefreshTokenService refreshTokenService, EmailService emailService, AuditService auditService,
            @Value("${app.public-url}") String publicUrl) {
        this.tokens = tokens;
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.publicUrl = publicUrl;
    }

    // Emails a link if the address belongs to an open account; otherwise does nothing. The caller answers the same
    // either way, so this can't be used to find out who has an account.
    @Transactional
    public void requestReset(String email) {
        userService.findByEmail(email.strip().toLowerCase(Locale.ROOT)) // stored lower-cased, like at registration
                .filter(user -> user.getDeletedAt() == null)
                .ifPresent(this::sendLink);
    }

    private void sendLink(User user) {
        tokens.deleteUnusedOf(user.getId());
        String rawToken = OneTimeTokens.newToken();
        tokens.save(new PasswordResetToken(user, OneTimeTokens.hash(rawToken), Instant.now().plus(VALIDITY)));
        // The token after "#": browsers never send that part to a server, so it stays out of access logs (Worker,
        // Render) and Referer headers. The address comes from configuration, never from the request.
        String link = publicUrl + "/reset-password#token=" + rawToken;
        // In the language the person is using the site in right now (Accept-Language, one of fr/nl/en).
        emailService.queue(user.getEmail(), LocaleContextHolder.getLocale(), "passwordReset",
                user.getName(), link, VALIDITY.toMinutes());
        auditService.recordSecurityEvent("PASSWORD_RESET_REQUESTED", user, null);
    }

    // Sets the new password, uses up the link, and ends every session of the account: whoever may have been using
    // the old password is logged out everywhere.
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokens.findByTokenHash(OneTimeTokens.hash(rawToken))
                .orElseThrow(InvalidResetTokenException::new);
        if (tokens.use(token.getId(), Instant.now()) == 0) {
            throw new InvalidResetTokenException(); // expired or already used
        }
        User user = token.getUser();
        if (user.getDeletedAt() != null) {
            throw new InvalidResetTokenException();
        }
        userService.changePassword(user, newPassword);
        refreshTokenService.revokeAllSessionsOf(user.getId());
        auditService.recordSecurityEvent("PASSWORD_RESET", user, Map.of("sessionsEnded", true));
    }
}
