package club.asbl.asbl_club.audit;

import java.util.List;
import org.springframework.data.repository.Repository;

interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByAsblIdOrderByCreatedAtDesc(Long asblId);
}
