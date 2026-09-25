package club.asbl.asbl_club.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.UserService;
import java.util.List;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
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

    // Through the API, as the Angular app does it: the logged-in user (from the access token) is the actor.
    @Test
    void creatingAsbl_writesOneAuditLineWithActorAndPayload() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");
        String token = JsonPath.read(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/v1/asbls")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"denomination": "Mon Club", "bceNumber": "0123.456.789", "slug": "mon-club",
                                 "defaultLanguage": "fr"}
                                """))
                .andExpect(status().isCreated());

        Asbl asbl = asblService.findBySlug("mon-club").orElseThrow();
        List<AuditLog> logs = auditLogRepository.findByAsblId(asbl.getId(), PageRequest.of(0, 50)).getContent();

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
