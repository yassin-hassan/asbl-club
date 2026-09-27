package club.asbl.asbl_club.email;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Sends the outbox, one email per transaction: claim the next due one (locked), send it over SMTP, record the
// outcome. A failure (provider down, timeout) is retried later with growing delays; after the last attempt the email
// is marked FAILED. If the app stops between "sent" and "recorded", that email goes out twice: at-least-once, which
// is the price of never losing one.
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);
    private static final int BATCH = 20;
    // After the 1st failure wait 1 minute, then 5, 30, 2 hours; the 5th failure is final.
    static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(2));

    private final OutgoingEmailRepository outbox;
    private final JavaMailSender mailSender;
    private final EmailProperties properties;
    private final TransactionTemplate transaction;

    EmailSender(OutgoingEmailRepository outbox, JavaMailSender mailSender, EmailProperties properties,
            TransactionTemplate transaction) {
        this.outbox = outbox;
        this.mailSender = mailSender;
        this.properties = properties;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${email.send-every-ms}")
    void sendScheduled() {
        if (properties.senderEnabled()) {
            sendDue();
        }
    }

    // Sends what is due now, up to a batch; returns how many were handled (sent or failed an attempt).
    public int sendDue() {
        int handled = 0;
        while (handled < BATCH && Boolean.TRUE.equals(transaction.execute(status -> sendNext()))) {
            handled++;
        }
        return handled;
    }

    private boolean sendNext() {
        Instant now = Instant.now();
        return outbox.claimNextDue(now).map(email -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(properties.from());
                message.setTo(email.getRecipient());
                message.setSubject(email.getSubject());
                message.setText(email.getBody());
                mailSender.send(message);
                email.sent(now);
            } catch (MailException e) {
                boolean giveUp = email.getAttempts() >= RETRY_DELAYS.size();
                Instant retryAt = giveUp ? now : now.plus(RETRY_DELAYS.get(email.getAttempts()));
                email.failedAttempt(e.getMessage(), retryAt, giveUp);
                log.warn("Email {} not sent (attempt {}){}: {}", email.getId(), email.getAttempts(),
                        giveUp ? ", giving up" : "", e.getMessage());
            }
            return true;
        }).orElse(false);
    }
}
