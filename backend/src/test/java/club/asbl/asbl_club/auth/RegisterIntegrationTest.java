package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.email.EmailSender;
import club.asbl.asbl_club.user.UserService;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.mail.internet.MimeMessage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Sign-up with a confirmed email: the site always answers "check your inbox"; the email (received by GreenMail, a
// real SMTP server in the test) carries either a confirmation link or "you already have an account".
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "app.public-url=https://site.test"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RegisterIntegrationTest {

    @RegisterExtension
    static GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final Pattern LINK = Pattern.compile("https://site\\.test/verify-email#token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    JwtDecoder jwtDecoder;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EmailSender outbox;
    @Autowired
    jakarta.persistence.EntityManager entityManager;

    @Test
    void signingUp_sendsALink_whichConfirmsTheAddressAndLogsIn() throws Exception {
        register("Alice", "alice@club.test", "password123").andExpect(status().isAccepted())
                .andExpect(content().string(""));

        String body = verify(linkFromTheEmailTo("alice@club.test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andReturn().getResponse().getContentAsString();

        String subject = jwtDecoder.decode(JsonPath.read(body, "$.accessToken")).getSubject();
        assertThat(subject).isEqualTo(userService.getByEmail("alice@club.test").getPublicId().toString());
        assertThat(audited("EMAIL_VERIFIED")).isEqualTo(1);
        login("alice@club.test", "password123").andExpect(status().isOk()); // and the password works from now on
    }

    @Test
    void beforeConfirming_theRightPasswordIsRefusedWithAReason_theWrongOneLearnsNothing() throws Exception {
        register("Alice", "alice@club.test", "password123");

        login("alice@club.test", "password123")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
        login("alice@club.test", "wrong-password").andExpect(status().isUnauthorized()); // same as any bad login
    }

    // Account enumeration: the answer doesn't say the address is taken; the owner is told by email instead.
    @Test
    void anAddressWithAnAccount_getsTheSameAnswer_andItsOwnerAnEmail() throws Exception {
        userService.register("Bob", "bob@club.test", "bobs-password");

        register("Mallory", "bob@club.test", "another-password")
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        outbox.sendDue();
        MimeMessage email = lastEmail();
        assertThat(email.getSubject()).isEqualTo("Vous avez déjà un compte asbl.club");
        assertThat(email.getContent().toString()).contains("https://site.test/login", "https://site.test/forgot-password")
                .doesNotContain("verify-email");
        login("bob@club.test", "bobs-password").andExpect(status().isOk()); // nothing changed for Bob
        login("bob@club.test", "another-password").andExpect(status().isUnauthorized());
    }

    // Until someone proves they own the inbox, the account is nobody's: signing up again replaces the details, and
    // only the latest link works.
    @Test
    void signingUpAgainBeforeConfirming_replacesTheDetails_andOnlyTheLatestLinkWorks() throws Exception {
        register("Alice", "alice@club.test", "first-password");
        String first = linkFromTheEmailTo("alice@club.test");
        register("Alice B.", "alice@club.test", "second-password");
        String second = linkFromTheEmailTo("alice@club.test");

        verify(first).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
        verify(second).andExpect(status().isOk());
        login("alice@club.test", "second-password").andExpect(status().isOk());
    }

    @Test
    void theLinkWorksOnce_andExpires() throws Exception {
        register("Alice", "alice@club.test", "password123");
        String token = linkFromTheEmailTo("alice@club.test");
        verify(token).andExpect(status().isOk());
        verify(token).andExpect(status().isBadRequest());

        register("Carol", "carol@club.test", "password123");
        String expired = linkFromTheEmailTo("carol@club.test");
        jdbcTemplate.update("UPDATE email_verification_tokens SET expires_at = now() - interval '1 minute' "
                + "WHERE used_at IS NULL");
        verify(expired).andExpect(status().isBadRequest());
    }

    @Test
    void theLinkCanBeSentAgain_forAnAccountWaitingForIt_only() throws Exception {
        register("Alice", "alice@club.test", "password123");
        linkFromTheEmailTo("alice@club.test");

        resend("alice@club.test").andExpect(status().isAccepted());
        assertThat(outbox.sendDue()).isEqualTo(1);
        resend("nobody@club.test").andExpect(status().isAccepted());
        userService.register("Bob", "bob@club.test", "bobs-password");
        resend("bob@club.test").andExpect(status().isAccepted()); // already confirmed: nothing to send
        assertThat(outbox.sendDue()).isZero();
    }

    // Emails sent without a request to take the language from (a ticket confirmed by Stripe) use the last one seen.
    @Test
    void theLanguageTheSiteIsUsedIn_isRememberedForLaterEmails() throws Exception {
        userService.register("Bob", "bob@club.test", "bobs-password");

        mockMvc.perform(post("/api/v1/auth/login").header("Accept-Language", "nl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"bob@club.test\", \"password\": \"bobs-password\"}"))
                .andExpect(status().isOk());

        entityManager.flush(); // written by JPA in this test's transaction; the SQL check reads the table
        assertThat(jdbcTemplate.queryForObject("SELECT language FROM users WHERE email = 'bob@club.test'",
                String.class)).isEqualTo("nl");
    }

    @Test
    void tooShortPassword_isRejectedWithTheFieldNamed() throws Exception {
        register("Alice", "alice@club.test", "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void invalidEmail_isRejectedWithTheFieldNamed() throws Exception {
        register("Alice", "not-an-email", "password123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    // Sends the outbox and reads the link's token from the last email to this address.
    private String linkFromTheEmailTo(String to) throws Exception {
        outbox.sendDue();
        MimeMessage email = lastEmail();
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo(to);
        Matcher link = LINK.matcher(email.getContent().toString());
        assertThat(link.find()).as("a confirmation link in the email").isTrue();
        return link.group(1);
    }

    private MimeMessage lastEmail() {
        MimeMessage[] received = mailServer.getReceivedMessages();
        return received[received.length - 1];
    }

    private ResultActions register(String name, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}".formatted(name, email, password)));
    }

    private ResultActions verify(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private int audited(String action) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ?", Integer.class, action);
    }
}
