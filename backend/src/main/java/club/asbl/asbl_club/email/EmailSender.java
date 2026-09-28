package club.asbl.asbl_club.email;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    // Plain text, UTF-8; with a ticket, a second part: the QR code as a PNG attachment (works in every mail program,
    // unlike an image embedded in HTML).
    private MimeMessage mimeMessage(OutgoingEmail email) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        boolean withTicket = email.getQrCode() != null;
        MimeMessageHelper helper = new MimeMessageHelper(message, withTicket, "UTF-8");
        helper.setFrom(properties.from());
        helper.setTo(email.getRecipient());
        helper.setSubject(email.getSubject());
        helper.setText(email.getBody(), false);
        if (withTicket) {
            helper.addAttachment(email.getQrFileName(), new ByteArrayResource(QrCodes.png(email.getQrCode())),
                    "image/png");
        }
        return message;
    }

    private boolean sendNext() {
        Instant now = Instant.now();
        return outbox.claimNextDue(now).map(email -> {
            try {
                mailSender.send(mimeMessage(email));
                email.sent(now);
            } catch (MailException | MessagingException e) {
                boolean giveUp = email.getAttempts() >= RETRY_DELAYS.size();
                Instant retryAt = giveUp ? now : now.plus(RETRY_DELAYS.get(email.getAttempts()));
                email.failedAttempt(e.getMessage(), retryAt, giveUp);
                if (giveUp) { // an error: someone never got their link or ticket (alerted, see application.yaml)
                    log.error("Email {} not sent after {} attempts, giving up: {}", email.getId(),
                            email.getAttempts(), e.getMessage());
                } else { // a warning: it will be retried
                    log.warn("Email {} not sent (attempt {}): {}", email.getId(), email.getAttempts(),
                            e.getMessage());
                }
            }
            return true;
        }).orElse(false);
    }
}
