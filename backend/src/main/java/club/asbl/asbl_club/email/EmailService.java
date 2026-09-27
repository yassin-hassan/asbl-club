package club.asbl.asbl_club.email;

import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Puts an email in the outbox, written in the recipient's language from the message files (plain text: it reads
// well everywhere and is what spam filters trust most). Sending happens later, in the background (EmailSender).
@Service
@EnableConfigurationProperties(EmailProperties.class)
public class EmailService {

    private final OutgoingEmailRepository outbox;
    private final MessageSource messages;

    EmailService(OutgoingEmailRepository outbox, MessageSource messages) {
        this.outbox = outbox;
        this.messages = messages;
    }

    // MANDATORY: always part of the caller's transaction, so the email exists if and only if the action committed.
    @Transactional(propagation = Propagation.MANDATORY)
    public void queue(String recipient, Locale locale, String template, Object... args) {
        String subject = messages.getMessage("email." + template + ".subject", args, locale);
        String body = messages.getMessage("email." + template + ".body", args, locale)
                + "\n\n" + messages.getMessage("email.signature", null, locale);
        outbox.save(new OutgoingEmail(recipient, subject, body));
    }
}
