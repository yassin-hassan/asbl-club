package club.asbl.asbl_club.audit;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    List<AuditLog> findByAsblIdOrderByCreatedAtDesc(Long asblId);

    List<AuditLog> findTop200ByOrderByCreatedAtDesc();

    // Only age-based deletion, never a targeted row. The 3-year boundary must
    // stay in sync with the trigger in V8__protect_audit_logs.sql.
    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE created_at < now() - INTERVAL '3 years'", nativeQuery = true)
    int deleteOlderThanRetention();
}
