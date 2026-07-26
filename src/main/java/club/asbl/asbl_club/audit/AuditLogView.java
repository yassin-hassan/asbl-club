package club.asbl.asbl_club.audit;

import java.time.Instant;

public record AuditLogView(
        Instant createdAt,
        String actorEmail,
        String action,
        String entityType,
        Long entityId,
        String ip) {
}
