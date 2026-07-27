package club.asbl.asbl_club.audit;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditRetentionIntegrationTest {

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void purge_removesEntriesPastRetention_andKeepsRecentOnes() {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (action, created_at) VALUES ('OLD', now() - INTERVAL '4 years')");
        jdbcTemplate.update("INSERT INTO audit_logs (action) VALUES ('RECENT')");

        int removed = auditLogRepository.deleteOlderThanRetention();

        assertThat(removed).isEqualTo(1);
        Integer remaining = jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs", Integer.class);
        assertThat(remaining).isEqualTo(1);
    }
}
