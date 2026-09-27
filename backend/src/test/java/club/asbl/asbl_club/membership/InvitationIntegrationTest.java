package club.asbl.asbl_club.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.email.EmailSender;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
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

// Personal invitations: sent by an administrator, joining directly, only for the invited address.
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "app.public-url=https://site.test"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class InvitationIntegrationTest {

    @RegisterExtension
    static GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final Pattern LINK = Pattern.compile("https://site\\.test/invitation#token=([A-Za-z0-9_-]+)");
    private static final String INVITATIONS = "/api/v1/asbls/mon-club/manage/invitations";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    EmailSender outbox;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;

    Asbl club;
    User bob;

    @BeforeEach
    void anAssociationRunByAlice() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        bob = userService.register("Bob", "bob@club.test", "password123");
    }

    @Test
    void anInvitation_arrivesByEmail_andJoinsDirectly() throws Exception {
        invite("Bob@Club.test").andExpect(status().isAccepted());
        String token = tokenFromTheEmailTo("bob@club.test");
        assertThat(lastEmail().getSubject()).isEqualTo("Alice vous invite à rejoindre Mon Club sur asbl.club");

        call(post("/api/v1/invitations/preview"), null, "{\"token\": \"" + token + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.association").value("Mon Club"))
                .andExpect(jsonPath("$.invitedBy").value("Alice"))
                .andExpect(jsonPath("$.email").value("b•••b@club.test"));
        accept(token, "bob@club.test").andExpect(status().isOk()).andExpect(jsonPath("$.slug").value("mon-club"));

        call(get("/api/v1/asbls/mon-club/members"), "bob@club.test", null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.myRole").value("MEMBER"));
        accept(token, "bob@club.test").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INVITATION")); // used once
        assertThat(audited("MEMBER_INVITED")).isEqualTo(1);
        assertThat(audited("MEMBER_JOINED_BY_INVITATION")).isEqualTo(1);
    }

    // A forwarded email is useless: the invitation only works for an account with the invited address.
    @Test
    void someoneElse_cannotUseTheInvitation() throws Exception {
        userService.register("Mallory", "mallory@club.test", "password123");
        invite("bob@club.test");
        String token = tokenFromTheEmailTo("bob@club.test");

        accept(token, "mallory@club.test").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WRONG_ACCOUNT"));
        accept(token, "bob@club.test").andExpect(status().isOk()); // still valid for Bob
    }

    @Test
    void anActiveMember_isNotInvitedAgain() throws Exception {
        invite("alice@club.test").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void anExcludedMember_canBeInvitedBack() throws Exception {
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'EXCLUDED')",
                bob.getId(), club.getId());
        invite("bob@club.test").andExpect(status().isAccepted());
        accept(tokenFromTheEmailTo("bob@club.test"), "bob@club.test").andExpect(status().isOk());

        entityManager.flush(); // changed through JPA in this test's transaction; the check reads the table
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM memberships WHERE user_id = ?", String.class,
                bob.getId())).isEqualTo("ACTIVE");
    }

    @Test
    void invitingAgain_replacesTheEarlierLink_andCancelling_disablesIt() throws Exception {
        invite("bob@club.test");
        String first = tokenFromTheEmailTo("bob@club.test");
        invite("bob@club.test");
        String second = tokenFromTheEmailTo("bob@club.test");
        accept(first, "bob@club.test").andExpect(status().isBadRequest());

        String list = call(get(INVITATIONS), "alice@club.test", null)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("bob@club.test"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(list, "$[0].id");
        call(delete(INVITATIONS + "/" + id), "alice@club.test", null).andExpect(status().isNoContent());

        accept(second, "bob@club.test").andExpect(status().isBadRequest());
        call(get(INVITATIONS), "alice@club.test", null).andExpect(jsonPath("$.length()").value(0));
    }

    // Anti-spam: each invitation is an email; an association can send a limited number a day.
    @Test
    void thereIsADailyLimitPerAssociation() throws Exception {
        for (int i = 0; i < InvitationService.DAILY_LIMIT_PER_ASSOCIATION; i++) {
            jdbcTemplate.update("INSERT INTO invitations (asbl_id, email, token_hash, invited_by, expires_at) "
                    + "VALUES (?, ?, ?, ?, now() + interval '14 days')", club.getId(), "p" + i + "@club.test",
                    "hash-" + i, bob.getId());
        }
        invite("carol@club.test").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVITATION_LIMIT"));
    }

    @Test
    void onlyAdministratorsInvite_andAnUnknownLinkShowsNothing() throws Exception {
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                bob.getId(), club.getId());
        call(post(INVITATIONS), "bob@club.test", "{\"email\": \"carol@club.test\"}").andExpect(status().isForbidden());
        call(post("/api/v1/invitations/preview"), null, "{\"token\": \"nope\"}").andExpect(status().isNotFound());
        call(post("/api/v1/invitations/accept"), null, "{\"token\": \"nope\"}").andExpect(status().isUnauthorized());
    }

    private ResultActions invite(String email) throws Exception {
        return call(post(INVITATIONS), "alice@club.test", "{\"email\": \"" + email + "\"}");
    }

    private ResultActions accept(String token, String as) throws Exception {
        return call(post("/api/v1/invitations/accept"), as, "{\"token\": \"" + token + "\"}");
    }

    private String tokenFromTheEmailTo(String to) throws Exception {
        outbox.sendDue();
        MimeMessage email = lastEmail();
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo(to);
        Matcher link = LINK.matcher(email.getContent().toString());
        assertThat(link.find()).as("an invitation link").isTrue();
        return link.group(1);
    }

    private MimeMessage lastEmail() {
        MimeMessage[] received = mailServer.getReceivedMessages();
        return received[received.length - 1];
    }

    private int audited(String action) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND asbl_id = ?",
                Integer.class, action, club.getId());
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String as, String json) throws Exception {
        if (as != null) {
            request.header("Authorization", "Bearer " + tokenFor(as));
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
