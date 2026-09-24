package club.asbl.asbl_club.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

// Records password logins (the login form and the API's login endpoint), successful or not.
// Spring Security fires the same events each time it checks an access token on an API call; those are not
// logins and are ignored, or the journal would get one fake "login" per request.
@Component
class AuthenticationAuditListener {

    private final AuditService auditService;

    AuthenticationAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        if (!isPasswordLogin(event.getAuthentication())) {
            return;
        }
        auditService.recordLogin("LOGIN_SUCCEEDED",
                event.getAuthentication().getName(), ipOf(event.getAuthentication()));
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        if (!isPasswordLogin(event.getAuthentication())) {
            return;
        }
        auditService.recordLogin("LOGIN_FAILED",
                event.getAuthentication().getName(), ipOf(event.getAuthentication()));
    }

    private static boolean isPasswordLogin(Authentication authentication) {
        return authentication instanceof UsernamePasswordAuthenticationToken;
    }

    private String ipOf(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return null;
    }
}
