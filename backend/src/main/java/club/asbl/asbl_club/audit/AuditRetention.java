package club.asbl.asbl_club.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AuditRetention {

    private static final Logger log = LoggerFactory.getLogger(AuditRetention.class);

    private final AuditLogRepository auditLogRepository;

    AuditRetention(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    void purgeExpiredEntries() {
        int removed = auditLogRepository.deleteOlderThanRetention();
        if (removed > 0) {
            log.info("Purged {} audit log entries past the retention period", removed);
        }
    }
}
