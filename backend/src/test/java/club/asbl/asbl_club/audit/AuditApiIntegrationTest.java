package club.asbl.asbl_club.audit;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuditApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Asbl club;
    Asbl otherClub;
    String adminToken;
    String memberToken;
    String superAdminToken;

    @BeforeEach
    void twoAssociationsWithTheirAdminsAMemberAndASuperAdmin() throws Exception {
        User admin = userService.register("Admin", "admin@club.test", "password123");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        User otherAdmin = userService.register("Other", "other@club.test", "password123");
        otherClub = asblService.createAsbl(otherAdmin, "Autre Club", "0987.654.321", "autre-club", "fr");
        User member = userService.register("Member", "member@club.test", "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                member.getId(), club.getId());
        userService.registerSuperAdmin("Root", "root@club.test", "password123");
        adminToken = tokenFor("admin@club.test");
        memberToken = tokenFor("member@club.test");
        superAdminToken = tokenFor("root@club.test");
    }

    @Test
    void anAdmin_readsTheirAssociationsJournal_withoutIpAddresses_norOtherAssociations() throws Exception {
        seed(club, "MINE", 1);
        seed(otherClub, "NOT_MINE", 1);

        journal("/api/v1/asbls/mon-club/manage/audit", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").value("Mon Club"))
                .andExpect(jsonPath("$.entries[*].action").value(hasItem("MINE")))
                .andExpect(jsonPath("$.entries[*].action").value(not(hasItem("NOT_MINE"))))
                .andExpect(jsonPath("$.entries[0].ip").doesNotExist());
    }

    @Test
    void theJournal_comesNewestFirst_fiftyEntriesAPage() throws Exception {
        seed(club, "SEEDED", 60); // plus the association's own creation entry

        journal("/api/v1/asbls/mon-club/manage/audit", adminToken)
                .andExpect(jsonPath("$.entries.length()").value(50))
                .andExpect(jsonPath("$.entries[0].action").value("SEEDED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalEntries").value(61));
        journal("/api/v1/asbls/mon-club/manage/audit?page=1", adminToken)
                .andExpect(jsonPath("$.entries.length()").value(11))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void plainMembersAndOtherAdmins_cannotReadAnAssociationsJournal() throws Exception {
        journal("/api/v1/asbls/mon-club/manage/audit", memberToken).andExpect(status().isForbidden());
        journal("/api/v1/asbls/autre-club/manage/audit", adminToken).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/asbls/mon-club/manage/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSuperAdmin_readsThePlatformJournal_withIpAddresses() throws Exception {
        seed(otherClub, "ANYWHERE", 1);

        journal("/api/v1/admin/audit", superAdminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].action").value("ANYWHERE"))
                .andExpect(jsonPath("$.entries[0].asbl").value("Autre Club"))
                .andExpect(jsonPath("$.entries[0].ip").value("203.0.113.7"));
    }

    // Being an association's administrator gives no access to the platform journal.
    @Test
    void associationAdmins_cannotReadThePlatformJournal() throws Exception {
        journal("/api/v1/admin/audit", adminToken).andExpect(status().isForbidden());
    }

    // Later timestamps than the entries the setup wrote, so the seeded rows come first.
    private void seed(Asbl asbl, String action, int count) {
        for (int i = 0; i < count; i++) {
            jdbcTemplate.update("INSERT INTO audit_logs (action, asbl_id, ip, created_at) "
                    + "VALUES (?, ?, '203.0.113.7', now() + make_interval(hours => 1, secs => ?))", action, asbl.getId(), i + 1);
        }
    }

    private ResultActions journal(String url, String token) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
