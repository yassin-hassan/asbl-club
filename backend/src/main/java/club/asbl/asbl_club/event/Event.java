package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "asbl_id", nullable = false)
    private Asbl asbl;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    // Buyers may cancel their ticket (and be refunded) until this many days before the start; 0: never.
    @Column(name = "cancellation_days", nullable = false)
    private int cancellationDays;

    protected Event() {
    }

    public Long getId() {
        return id;
    }

    public Asbl getAsbl() {
        return asbl;
    }

    public void setAsbl(Asbl asbl) {
        this.asbl = asbl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public EventVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(EventVisibility visibility) {
        this.visibility = visibility;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public int getCancellationDays() {
        return cancellationDays;
    }

    public void setCancellationDays(int cancellationDays) {
        this.cancellationDays = cancellationDays;
    }

    // The last moment a buyer may cancel their ticket, or empty when tickets aren't refundable on request.
    public Optional<Instant> cancellableUntil() {
        return cancellationDays == 0 ? Optional.empty()
                : Optional.of(startsAt.minus(Duration.ofDays(cancellationDays)));
    }
}
