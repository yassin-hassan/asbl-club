package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.email.EmailSender;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.Cookie;
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
import org.springframework.transaction.annotation.Transactional;

// "Forgot password", end to end: the request, the email (received by a real SMTP server, GreenMail), the link.
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "app.public-url=https://site.test"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PasswordResetIntegrationTest {

    @RegisterExtension
    static GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP);

    private static final Pattern LINK = Pattern.compile("https://site\\.test/reset-password#token=([A-Za-z0-9_-]+)");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EmailSender outbox; // sends what's due at once, instead of waiting for the background job

    User alice;

    @BeforeEach
    void anAccount() {
        alice = userService.register("Alice", "alice@club.test", "old-password");
    }

    @Test
    void theLinkLetsYouChooseANewPassword_andEndsEverySession() throws Exception {
        Cookie session = login("alice@club.test", "old-password").andReturn().getResponse().getCookie("refresh_token");

        requestReset("alice@club.test").andExpect(status().isAccepted());
        String token = tokenFromTheEmail("alice@club.test");
        confirm(token, "brand-new-password").andExpect(status().isNoContent());

        login("alice@club.test", "brand-new-password").andExpect(status().isOk());
        login("alice@club.test", "old-password").andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        assertThat(audited("PASSWORD_RESET_REQUESTED")).isEqualTo(1);
        assertThat(audited("PASSWORD_RESET")).isEqualTo(1);
        // Sent: the email's body (with the one-time link) is gone from the outbox; the row stays as a trace.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM email_outbox WHERE status = 'SENT' AND body IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void anUnknownAddress_getsTheSameAnswer_andNoEmail() throws Exception {
        requestReset("nobody@club.test").andExpect(status().isAccepted());

        assertThat(outbox.sendDue()).isZero();
        assertThat(mailServer.getReceivedMessages()).isEmpty();
    }

    @Test
    void theAddressIsMatchedWhateverItsCase() throws Exception {
        requestReset("Alice@Club.TEST").andExpect(status().isAccepted());

        assertThat(tokenFromTheEmail("alice@club.test")).isNotBlank();
    }

    @Test
    void theLinkWorksOnce() throws Exception {
        requestReset("alice@club.test");
        String token = tokenFromTheEmail("alice@club.test");
        confirm(token, "brand-new-password").andExpect(status().isNoContent());

        confirm(token, "another-password")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
    }

    @Test
    void anExpiredLink_isRefused() throws Exception {
        requestReset("alice@club.test");
        String token = tokenFromTheEmail("alice@club.test");
        jdbcTemplate.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute'");

        confirm(token, "brand-new-password").andExpect(status().isBadRequest());
        login("alice@club.test", "old-password").andExpect(status().isOk()); // nothing changed
    }

    @Test
    void aNewRequest_replacesTheEarlierLink() throws Exception {
        requestReset("alice@club.test");
        String first = tokenFromTheEmail("alice@club.test");
        requestReset("alice@club.test");
        String second = tokenFromTheEmail("alice@club.test");

        confirm(first, "brand-new-password").andExpect(status().isBadRequest());
        confirm(second, "brand-new-password").andExpect(status().isNoContent());
    }

    @Test
    void aClosedAccount_getsNoEmail() throws Exception {
        userService.anonymizeAndClose(alice);

        requestReset("alice@club.test").andExpect(status().isAccepted());
        assertThat(outbox.sendDue()).isZero();
    }

    // Host header injection: the link must point to the configured site, whatever the request claims to be.
    @Test
    void theLinkPointsToTheConfiguredSite_notToTheRequestsHost() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset").header("Host", "evil.test")
                        .header("X-Forwarded-Host", "evil.test")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"alice@club.test\"}"))
                .andExpect(status().isAccepted());

        outbox.sendDue();
        String body = mailServer.getReceivedMessages()[0].getContent().toString();
        assertThat(body).contains("https://site.test/reset-password#token=").doesNotContain("evil.test");
    }

    @Test
    void theEmailIsInTheLanguageTheSiteIsUsedIn() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset").header("Accept-Language", "nl")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"alice@club.test\"}"));

        outbox.sendDue();
        assertThat(mailServer.getReceivedMessages()[0].getSubject()).isEqualTo("Stel je asbl.club-wachtwoord opnieuw in");
    }

    @Test
    void aTooShortPassword_isRefused_andTheLinkStaysUsable() throws Exception {
        requestReset("alice@club.test");
        String token = tokenFromTheEmail("alice@club.test");

        confirm(token, "short").andExpect(status().isBadRequest());
        confirm(token, "long-enough-password").andExpect(status().isNoContent());
    }

    // Sends the outbox (as the background job would) and reads the link from the last email to this address.
    private String tokenFromTheEmail(String to) throws Exception {
        outbox.sendDue();
        MimeMessage[] received = mailServer.getReceivedMessages();
        MimeMessage last = received[received.length - 1];
        assertThat(last.getAllRecipients()[0].toString()).isEqualTo(to);
        Matcher link = LINK.matcher(last.getContent().toString()); // decoded, as a mail program shows it
        assertThat(link.find()).as("a reset link in the email").isTrue();
        return link.group(1);
    }

    private ResultActions requestReset(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"" + email + "\"}"));
    }

    private ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\", \"password\": \"%s\"}".formatted(token, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private int audited(String action) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = ? AND user_id = ?",
                Integer.class, action, alice.getId());
    }
}
