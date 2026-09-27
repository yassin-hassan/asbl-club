package club.asbl.asbl_club.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// When the mail provider fails, an email is retried later, with growing delays, then given up (not lost silently:
// it stays FAILED in the outbox with the last error).
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class EmailSenderIntegrationTest {

    @Autowired
    EmailService emailService;
    @Autowired
    EmailSender emailSender;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @MockitoBean
    JavaMailSender mailSender;

    @Test
    void aFailedSend_isRetriedLater_thenGivenUpAfterTheLastAttempt() {
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
        doThrow(new MailSendException("provider unreachable")).when(mailSender).send(any(MimeMessage.class));
        emailService.queue("alice@club.test", Locale.ENGLISH, "passwordReset", "Alice", "https://site.test/x", 30);

        emailSender.sendDue();
        assertThat(row("status")).isEqualTo("PENDING");
        assertThat(row("attempts")).isEqualTo("1");
        assertThat(row("last_error")).isEqualTo("provider unreachable");
        Instant retryAt = jdbcTemplate.queryForObject("SELECT next_attempt_at FROM email_outbox", Instant.class);
        assertThat(retryAt).isAfter(Instant.now().plus(Duration.ofSeconds(50))); // about a minute later
        assertThat(emailSender.sendDue()).as("not due yet").isZero();

        for (int attempt = 2; attempt <= EmailSender.RETRY_DELAYS.size() + 1; attempt++) {
            // Time passes: due an hour ago. Not "now()": that's the database's clock, and Docker's can run seconds
            // ahead of the JVM's, which would leave the email "not due yet" for the sender (it asks with Java's clock).
            jdbcTemplate.update("UPDATE email_outbox SET next_attempt_at = now() - interval '1 hour'");
            emailSender.sendDue();
        }
        assertThat(row("status")).isEqualTo("FAILED");
        assertThat(row("attempts")).isEqualTo(String.valueOf(EmailSender.RETRY_DELAYS.size() + 1));
        assertThat(row("body")).isNull(); // the one-time link doesn't linger
    }

    @Test
    void theEmailIsWrittenInTheRequestedLanguage() {
        emailService.queue("alice@club.test", Locale.FRENCH, "passwordReset", "Alice", "https://site.test/x", 30);

        assertThat(row("subject")).isEqualTo("Réinitialiser votre mot de passe asbl.club");
        assertThat(row("body")).contains("Bonjour Alice", "https://site.test/x", "30 minutes", "L'équipe asbl.club")
                .doesNotContain("''"); // apostrophes come out single
    }

    private String row(String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + "::text FROM email_outbox", String.class);
    }
}
