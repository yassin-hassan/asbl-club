package club.asbl.asbl_club.audit;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    // Security events about a known user that happen without a password login (e.g. token theft signals).
    @Transactional
    public void recordSecurityEvent(String action, User user, Map<String, Object> payload) {
        auditLogRepository.save(new AuditLog(action, user, currentIp(), null, null, null, payload));
    }

    // Newest first; the ID breaks ties between entries written in the same instant, so pages never overlap.
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Transactional(readOnly = true)
    public Page<AuditLogView> journalOf(Asbl asbl, int page, int size) {
        return auditLogRepository.findByAsblId(asbl.getId(), PageRequest.of(page, size, NEWEST_FIRST)).map(this::toView);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogView> journalAll(int page, int size) {
        return auditLogRepository.findAllBy(PageRequest.of(page, size, NEWEST_FIRST)).map(this::toView);
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
        return userService.getAuthenticated(authentication);
    }

    private String currentIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        // Already the real client IP: the server resolves X-Forwarded-For from trusted proxies only
        // (server.forward-headers-strategy). Reading the header here would let any client fake it.
        return attributes.getRequest().getRemoteAddr();
    }
}
