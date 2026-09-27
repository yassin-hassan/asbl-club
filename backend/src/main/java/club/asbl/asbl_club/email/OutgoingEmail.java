package club.asbl.asbl_club.email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One email in the outbox (see V15). */
@Entity
@Table(name = "email_outbox")
class OutgoingEmail {

    enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutgoingEmail() {
    }

    OutgoingEmail(String recipient, String subject, String body) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    // Sent: the body goes (it may hold a one-time link); who, what and when stay as a trace.
    void sent(Instant now) {
        status = Status.SENT;
        sentAt = now;
        body = null;
        lastError = null;
    }

    void failedAttempt(String error, Instant retryAt, boolean giveUp) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (giveUp) {
            status = Status.FAILED;
            body = null;
        } else {
            nextAttemptAt = retryAt;
        }
    }

    Long getId() {
        return id;
    }

    String getRecipient() {
        return recipient;
    }

    String getSubject() {
        return subject;
    }

    String getBody() {
        return body;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }
}
