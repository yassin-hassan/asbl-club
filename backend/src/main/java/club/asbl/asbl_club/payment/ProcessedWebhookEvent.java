package club.asbl.asbl_club.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A Stripe event already handled (see V14). Written only through {@link ProcessedWebhookEventRepository#recordIfNew}. */
@Entity
@Table(name = "processed_webhook_events")
class ProcessedWebhookEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected ProcessedWebhookEvent() {
    }
}
