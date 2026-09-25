package club.asbl.asbl_club.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    // One page at a time, with each entry's author and association loaded in the same query (no query per row).
    @EntityGraph(attributePaths = {"user", "asbl"})
    Page<AuditLog> findByAsblId(Long asblId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "asbl"})
    Page<AuditLog> findAllBy(Pageable pageable);

    // Only age-based deletion, never a targeted row. The 3-year boundary must
    // stay in sync with the trigger in V8__protect_audit_logs.sql.
    @Modifying
    @Query(value = "DELETE FROM audit_logs WHERE created_at < now() - INTERVAL '3 years'", nativeQuery = true)
    int deleteOlderThanRetention();
}
