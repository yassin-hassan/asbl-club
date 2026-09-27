package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.email.EmailService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Sign-up with a confirmed email. The site answers "check your inbox" whatever happened; only the email differs: a
// link to confirm (new address), or "you already have an account" (address in use). So the form can't be used to
// find out who has an account, and every account's address is known to reach its owner.
@Service
class EmailVerificationService {

    static final Duration VALIDITY = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokens;
    private final UserService userService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final String publicUrl;

    EmailVerificationService(EmailVerificationTokenRepository tokens, UserService userService,
            EmailService emailService, AuditService auditService, @Value("${app.public-url}") String publicUrl) {
        this.tokens = tokens;
        this.userService = userService;
        this.emailService = emailService;
        this.auditService = auditService;
        this.publicUrl = publicUrl;
    }

    @Transactional
    public void signUp(String name, String email, String password) {
        UserService.SignUp signUp = userService.signUp(name, email, password);
        User user = signUp.user();
        if (signUp.alreadyVerified()) {
            emailService.queue(user.getEmail(), LocaleContextHolder.getLocale(), "alreadyRegistered",
                    user.getName(), publicUrl + "/login", publicUrl + "/forgot-password");
            auditService.recordSecurityEvent("SIGN_UP_WITH_EXISTING_EMAIL", user, null);
        } else {
            userService.rememberLanguage(user, LocaleContextHolder.getLocale());
            sendLink(user);
        }
    }

    // "Send the link again": only for an account still waiting for its confirmation; the answer is the same anyway.
    @Transactional
    public void resend(String email) {
        userService.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .filter(user -> user.getEmailVerifiedAt() == null && user.getDeletedAt() == null)
                .ifPresent(this::sendLink);
    }

    // Confirms the address behind the link and returns its account, which the caller logs in: clicking the link is
    // proof enough of owning the inbox, so no password is asked again.
    @Transactional
    public User verify(String rawToken) {
        EmailVerificationToken token = tokens.findByTokenHash(OneTimeTokens.hash(rawToken))
                .orElseThrow(InvalidVerificationTokenException::new);
        if (tokens.use(token.getId(), Instant.now()) == 0 || token.getUser().getDeletedAt() != null) {
            throw new InvalidVerificationTokenException(); // expired, already used, or the account is gone
        }
        User user = token.getUser();
        userService.markEmailVerified(user);
        auditService.recordSecurityEvent("EMAIL_VERIFIED", user, null);
        return user;
    }

    private void sendLink(User user) {
        tokens.deleteUnusedOf(user.getId());
        String rawToken = OneTimeTokens.newToken();
        tokens.save(new EmailVerificationToken(user, OneTimeTokens.hash(rawToken), Instant.now().plus(VALIDITY)));
        // As for password resets: the token after "#" never reaches a server's logs; the address is configured.
        emailService.queue(user.getEmail(), LocaleContextHolder.getLocale(), "verifyEmail", user.getName(),
                publicUrl + "/verify-email#token=" + rawToken, VALIDITY.toHours());
    }
}
