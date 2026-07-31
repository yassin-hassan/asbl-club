package club.asbl.asbl_club.membership;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AuthorizationBoundariesIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    MembershipRepository membershipRepository;

    private Asbl clubOwnedBySomeoneElse(String slug, String bce) {
        User founder = userService.register("Founder", "founder-" + slug + "@club.test", "password123");
        return asblService.createAsbl(founder, "Club " + slug, bce, slug, "fr");
    }

    @Test
    @WithMockUser(username = "outsider@club.test")
    void nonMemberCannotViewMembers() throws Exception {
        userService.register("Outsider", "outsider@club.test", "password123");
        clubOwnedBySomeoneElse("club-a", "0303.303.303");

        mockMvc.perform(get("/asbls/club-a/members")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "outsider2@club.test")
    void nonMemberCannotViewEvents() throws Exception {
        userService.register("Outsider", "outsider2@club.test", "password123");
        clubOwnedBySomeoneElse("club-b", "0404.404.404");

        mockMvc.perform(get("/asbls/club-b/events")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "member@club.test")
    void plainMemberCanViewMembersButNotTheAuditJournal() throws Exception {
        User member = userService.register("Member", "member@club.test", "password123");
        Asbl club = clubOwnedBySomeoneElse("club-c", "0505.505.505");
        Membership membership = new Membership();
        membership.setUser(member);
        membership.setAsbl(club);
        membership.setRole("MEMBER");
        membership.setCategory("FULL");
        membership.setStatus("ACTIVE");
        membership.setJoinedAt(LocalDate.now());
        membershipRepository.save(membership);

        mockMvc.perform(get("/asbls/club-c/members")).andExpect(status().isOk());
        mockMvc.perform(get("/asbls/club-c/audit")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "plain@club.test")
    void nonSuperAdminCannotOpenTheAdminJournal() throws Exception {
        mockMvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "super@club.test", roles = "SUPERADMIN")
    void superAdminCanOpenTheAdminJournal() throws Exception {
        mockMvc.perform(get("/admin/audit")).andExpect(status().isOk());
    }
}
