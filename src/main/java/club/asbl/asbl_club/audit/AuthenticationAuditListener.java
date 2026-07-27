package club.asbl.asbl_club.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
class AuthenticationAuditListener {

    private final AuditService auditService;

    AuthenticationAuditListener(AuditService auditService) {
        this.auditService = auditService;
    }

    @EventListener
    void onSuccess(AuthenticationSuccessEvent event) {
        auditService.recordLogin("LOGIN_SUCCEEDED",
                event.getAuthentication().getName(), ipOf(event.getAuthentication()));
    }

    @EventListener
    void onFailure(AbstractAuthenticationFailureEvent event) {
        auditService.recordLogin("LOGIN_FAILED",
                event.getAuthentication().getName(), ipOf(event.getAuthentication()));
    }

    private String ipOf(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            return details.getRemoteAddress();
        }
        return null;
    }
}
