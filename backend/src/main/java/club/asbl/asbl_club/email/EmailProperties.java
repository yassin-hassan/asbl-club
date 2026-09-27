package club.asbl.asbl_club.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

// email.from: the sender shown in the inbox. email.sender-enabled: whether this instance sends the outbox.
@ConfigurationProperties(prefix = "email")
record EmailProperties(String from, boolean senderEnabled) {
}
