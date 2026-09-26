package club.asbl.asbl_club.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import jakarta.persistence.EntityManager;
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

// Joining an association through its join link: a request, then an administrator's decision. And the access rule
// underneath: only an ACTIVE membership opens anything.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class JoinLinkIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    Asbl club;
    User bob;
    String adminToken;
    String memberToken;
    String bobToken;

    @BeforeEach
    void anAssociationWithAnAdminAMemberAndBob() throws Exception {
        User admin = userService.register("Admin", "admin@club.test", "password123");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        User member = userService.register("Member", "member@club.test", "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                member.getId(), club.getId());
        bob = userService.register("Bob", "bob@club.test", "password123");
        adminToken = tokenFor("admin@club.test");
        memberToken = tokenFor("member@club.test");
        bobToken = tokenFor("bob@club.test");
    }

    // --- access by status (the rule every page and endpoint relies on) ---

    @Test
    void onlyAnActiveMembership_opensTheAssociation() throws Exception {
        for (String status : new String[] {"PENDING", "EXCLUDED", "LEFT"}) {
            jdbcTemplate.update("DELETE FROM memberships WHERE user_id = ?", bob.getId());
            jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'ADMIN', ?)",
                    bob.getId(), club.getId(), status);

            // Even with the ADMIN role: the status decides.
            call(get("/api/v1/asbls/mon-club/members"), bobToken).andExpect(status().isForbidden());
            call(get("/api/v1/asbls/mon-club/manage/events"), bobToken).andExpect(status().isForbidden());
            call(get("/api/v1/asbls/mon-club/manage/join-link"), bobToken).andExpect(status().isForbidden());
        }
    }

    // --- the join link ---

    @Test
    void anAdmin_createsReplacesAndSwitchesOffTheLink_andOnlyTheCurrentOneWorks() throws Exception {
        call(get("/api/v1/asbls/mon-club/manage/join-link"), adminToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist());

        String first = newLink();
        String second = newLink();
        assertThat(second).isNotEqualTo(first).hasSizeGreaterThanOrEqualTo(43);

        call(get("/api/v1/join/" + first), bobToken).andExpect(status().isNotFound()); // replaced: dead at once
        call(get("/api/v1/join/" + second), bobToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").value("Mon Club"))
                .andExpect(jsonPath("$.status").doesNotExist());

        call(delete("/api/v1/asbls/mon-club/manage/join-link"), adminToken).andExpect(status().isNoContent());
        call(get("/api/v1/join/" + second), bobToken).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdmins_manageTheLinkAndTheRequests() throws Exception {
        String link = newLink();
        call(post("/api/v1/join/" + link), bobToken).andExpect(status().isOk());
        String bobId = bob.getPublicId().toString();

        call(get("/api/v1/asbls/mon-club/manage/join-link"), memberToken).andExpect(status().isForbidden());
        call(post("/api/v1/asbls/mon-club/manage/join-link"), memberToken).andExpect(status().isForbidden());
        call(delete("/api/v1/asbls/mon-club/manage/join-link"), memberToken).andExpect(status().isForbidden());
        call(post("/api/v1/asbls/mon-club/manage/members/" + bobId + "/approve"), memberToken)
                .andExpect(status().isForbidden());
        call(post("/api/v1/asbls/mon-club/manage/members/" + bobId + "/decline"), memberToken)
                .andExpect(status().isForbidden());
    }

    // --- the request and the decision ---

    @Test
    void aRequest_isPendingUntilApproved_thenTheNewMemberIsIn() throws Exception {
        String link = newLink();

        call(post("/api/v1/join/" + link), bobToken)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
        call(post("/api/v1/join/" + link), bobToken) // asking twice changes nothing
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));

        // Pending: no access yet; the admin sees the request, other members don't.
        call(get("/api/v1/asbls/mon-club/members"), bobToken).andExpect(status().isForbidden());
        call(get("/api/v1/asbls/mon-club/members"), adminToken)
                .andExpect(jsonPath("$.members[?(@.status == 'PENDING')].name").value(hasItem("Bob")));
        call(get("/api/v1/asbls/mon-club/members"), memberToken)
                .andExpect(jsonPath("$.members[*].name").value(not(hasItem("Bob"))));
        call(get("/api/v1/me/associations"), bobToken)
                .andExpect(jsonPath("$[0].status").value("PENDING"));

        call(post("/api/v1/asbls/mon-club/manage/members/" + bob.getPublicId() + "/approve"), adminToken)
                .andExpect(status().isNoContent());

        call(get("/api/v1/asbls/mon-club/members"), bobToken).andExpect(status().isOk());
        call(get("/api/v1/join/" + link), bobToken).andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(auditActions()).contains("JOIN_LINK_CREATED", "JOIN_REQUESTED", "JOIN_APPROVED");
    }

    @Test
    void aDeclinedRequest_disappears_andThePersonMayAskAgain() throws Exception {
        String link = newLink();
        call(post("/api/v1/join/" + link), bobToken);

        call(post("/api/v1/asbls/mon-club/manage/members/" + bob.getPublicId() + "/decline"), adminToken)
                .andExpect(status().isNoContent());

        call(get("/api/v1/me/associations"), bobToken).andExpect(jsonPath("$.length()").value(0));
        call(post("/api/v1/join/" + link), bobToken).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(auditActions()).contains("JOIN_DECLINED");
    }

    @Test
    void approvingOrDecliningSomethingThatIsntAPendingRequest_isNotFound() throws Exception {
        // An active member, and someone who never asked.
        User member = userService.findByEmail("member@club.test").orElseThrow();
        call(post("/api/v1/asbls/mon-club/manage/members/" + member.getPublicId() + "/approve"), adminToken)
                .andExpect(status().isNotFound());
        call(post("/api/v1/asbls/mon-club/manage/members/" + bob.getPublicId() + "/decline"), adminToken)
                .andExpect(status().isNotFound());
    }

    @Test
    void anExcludedMember_cannotAskAgain_butSomeoneWhoLeftCan() throws Exception {
        String link = newLink();
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'EXCLUDED')",
                bob.getId(), club.getId());
        call(post("/api/v1/join/" + link), bobToken)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("JOIN_REFUSED"));

        jdbcTemplate.update("UPDATE memberships SET status = 'LEFT' WHERE user_id = ?", bob.getId());
        entityManager.clear(); // the previous request left the EXCLUDED row in Hibernate's cache
        call(post("/api/v1/join/" + link), bobToken).andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void theLinkIsNeverWrittenToTheAuditLog() throws Exception {
        String link = newLink();

        Integer mentions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE payload::text LIKE ?", Integer.class, "%" + link + "%");
        assertThat(mentions).isZero();
    }

    private String newLink() throws Exception {
        String body = call(post("/api/v1/asbls/mon-club/manage/join-link"), adminToken)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private java.util.List<String> auditActions() {
        return jdbcTemplate.queryForList("SELECT action FROM audit_logs WHERE asbl_id = ?", String.class, club.getId());
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
