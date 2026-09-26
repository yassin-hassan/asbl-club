package club.asbl.asbl_club.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// Administrators manage members (roles, exclusion); members may leave. An association always keeps an active admin.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ManageMembersIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Asbl club;
    User admin;
    User member;
    String adminToken;
    String memberToken;

    @BeforeEach
    void anAssociationWithAnAdminAndAMember() throws Exception {
        admin = userService.register("Admin", "admin@club.test", "password123");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        member = userService.register("Member", "member@club.test", "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                member.getId(), club.getId());
        adminToken = tokenFor("admin@club.test");
        memberToken = tokenFor("member@club.test");
    }

    @Test
    void anAdmin_changesARole_andItsAudited() throws Exception {
        role(member, "TREASURER", adminToken).andExpect(status().isNoContent());

        call(get("/api/v1/asbls/mon-club/members"), memberToken)
                .andExpect(jsonPath("$.myRole").value("TREASURER"));
        assertThat(audited("MEMBER_ROLE_CHANGED")).isEqualTo(1);
    }

    @Test
    void theLastActiveAdmin_cannotBeDemoted_excludedOrLeave() throws Exception {
        role(admin, "MEMBER", adminToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));
        call(post("/api/v1/asbls/mon-club/leave"), adminToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_ADMIN"));

        // With a second admin, the first may step down.
        role(member, "ADMIN", adminToken).andExpect(status().isNoContent());
        role(admin, "MEMBER", adminToken).andExpect(status().isNoContent());
    }

    @Test
    void anAdmin_excludesAMember_whoLosesAccessAtOnce() throws Exception {
        call(post("/api/v1/asbls/mon-club/manage/members/" + member.getPublicId() + "/exclude"), adminToken)
                .andExpect(status().isNoContent());

        call(get("/api/v1/asbls/mon-club/members"), memberToken).andExpect(status().isForbidden());
        call(get("/api/v1/asbls/mon-club/members"), adminToken)
                .andExpect(jsonPath("$.members[?(@.name == 'Member')].status").value(hasItem("EXCLUDED")));
        assertThat(audited("MEMBER_EXCLUDED")).isEqualTo(1);
    }

    @Test
    void anAdmin_cannotExcludeThemselves() throws Exception {
        call(post("/api/v1/asbls/mon-club/manage/members/" + admin.getPublicId() + "/exclude"), adminToken)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NOT_ON_YOURSELF"));
    }

    @Test
    void aMember_leaves_andLosesAccess() throws Exception {
        call(post("/api/v1/asbls/mon-club/leave"), memberToken).andExpect(status().isNoContent());

        call(get("/api/v1/asbls/mon-club/members"), memberToken).andExpect(status().isForbidden());
        call(post("/api/v1/asbls/mon-club/leave"), memberToken).andExpect(status().isNotFound()); // already gone
        assertThat(audited("MEMBER_LEFT")).isEqualTo(1);
    }

    @Test
    void onlyAdmins_changeRolesOrExclude() throws Exception {
        role(admin, "MEMBER", memberToken).andExpect(status().isForbidden());
        call(post("/api/v1/asbls/mon-club/manage/members/" + admin.getPublicId() + "/exclude"), memberToken)
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownRole_isRejected() throws Exception {
        role(member, "OWNER", adminToken).andExpect(status().isBadRequest());
    }

    @Test
    void actingOnSomeoneWhoIsntAnActiveMember_isNotFound() throws Exception {
        User stranger = userService.register("Stranger", "stranger@club.test", "password123");
        role(stranger, "ADMIN", adminToken).andExpect(status().isNotFound());
    }

    private ResultActions role(User who, String role, String token) throws Exception {
        return call(put("/api/v1/asbls/mon-club/manage/members/" + who.getPublicId() + "/role")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\": \"" + role + "\"}"), token);
    }

    private int audited(String action) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND asbl_id = ?",
                Integer.class, action, club.getId());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
