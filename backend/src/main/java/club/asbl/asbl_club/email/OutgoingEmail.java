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

    // Optional: a ticket code to attach as a QR image, and the attachment's name (see V18).
    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "qr_file_name")
    private String qrFileName;

    protected OutgoingEmail() {
    }

    OutgoingEmail(String recipient, String subject, String body) {
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    OutgoingEmail withQrCode(String code, String fileName) {
        this.qrCode = code;
        this.qrFileName = fileName;
        return this;
    }

    // Sent: the body goes (it may hold a one-time link); who, what and when stay as a trace.
    void sent(Instant now) {
        status = Status.SENT;
        sentAt = now;
        body = null;
        qrCode = null; // a ticket code lets someone in: it doesn't stay here either
        lastError = null;
    }

    void failedAttempt(String error, Instant retryAt, boolean giveUp) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (giveUp) {
            status = Status.FAILED;
            body = null;
            qrCode = null;
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

    String getQrCode() {
        return qrCode;
    }

    String getQrFileName() {
        return qrFileName;
    }

    Status getStatus() {
        return status;
    }

    int getAttempts() {
        return attempts;
    }
}
