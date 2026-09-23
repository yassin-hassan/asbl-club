package club.asbl.asbl_club.audit;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserService userService;

    AuditService(AuditLogRepository auditLogRepository, UserService userService) {
        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
    }

    @Transactional
    public void record(String action, Asbl asbl, String entityType, Long entityId, Map<String, Object> payload) {
        AuditLog log = new AuditLog(action, currentUser(), currentIp(), asbl, entityType, entityId, payload);
        auditLogRepository.save(log);
    }

    @Transactional
    public void record(String action, Asbl asbl) {
        record(action, asbl, null, null, null);
    }

    @Transactional
    public void recordSystem(String action, Asbl asbl, String entityType, Long entityId, Map<String, Object> payload) {
        auditLogRepository.save(new AuditLog(action, null, null, asbl, entityType, entityId, payload));
    }

    @Transactional
    public void recordLogin(String action, String email, String ip) {
        User user = email == null ? null : userService.findByEmail(email).orElse(null);
        Map<String, Object> payload = email == null ? null : Map.of("email", email);
        auditLogRepository.save(new AuditLog(action, user, ip, null, null, null, payload));
    }

    @Transactional(readOnly = true)
    public List<AuditLogView> journalOf(Asbl asbl) {
        return auditLogRepository.findByAsblIdOrderByCreatedAtDesc(asbl.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditLogView> journalAll() {
        return auditLogRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    private AuditLogView toView(AuditLog log) {
        String actorEmail = log.getUser() == null ? null : log.getUser().getEmail();
        String asbl = log.getAsbl() == null ? null : log.getAsbl().getDenomination();
        return new AuditLogView(log.getCreatedAt(), asbl, actorEmail, log.getAction(),
                log.getEntityType(), log.getEntityId(), log.getIp());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return userService.getByEmail(authentication.getName());
    }

    private String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
