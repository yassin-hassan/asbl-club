package club.asbl.asbl_club.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditLogIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @WithMockUser(username = "alice@club.test")
    void creatingAsbl_writesOneAuditLineWithActorAndPayload() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");

        mockMvc.perform(post("/asbls").with(csrf())
                        .param("denomination", "Mon Club")
                        .param("bceNumber", "0123.456.789")
                        .param("slug", "mon-club")
                        .param("defaultLanguage", "fr"))
                .andExpect(status().is3xxRedirection());

        Asbl asbl = asblService.findBySlug("mon-club").orElseThrow();
        List<AuditLog> logs = auditLogRepository.findByAsblIdOrderByCreatedAtDesc(asbl.getId());

        assertThat(logs).hasSize(1);
        AuditLog log = logs.get(0);
        assertThat(log.getAction()).isEqualTo("ASBL_CREATED");
        assertThat(log.getEntityType()).isEqualTo("Asbl");
        assertThat(log.getEntityId()).isEqualTo(asbl.getId());
        assertThat(log.getUser().getEmail()).isEqualTo("alice@club.test");
        assertThat(log.getPayload()).containsEntry("denomination", "Mon Club");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void auditRows_cannotBeUpdated() {
        jdbcTemplate.update("INSERT INTO audit_logs (action) VALUES ('TEST')");

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE audit_logs SET action = 'TAMPERED'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void recentAuditRows_cannotBeDeleted() {
        jdbcTemplate.update("INSERT INTO audit_logs (action) VALUES ('TEST')");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM audit_logs WHERE action = 'TEST'"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void auditRows_pastRetention_canBeDeleted() {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (action, created_at) VALUES ('OLD', now() - INTERVAL '4 years')");

        int deleted = jdbcTemplate.update("DELETE FROM audit_logs WHERE action = 'OLD'");

        assertThat(deleted).isEqualTo(1);
    }
}
