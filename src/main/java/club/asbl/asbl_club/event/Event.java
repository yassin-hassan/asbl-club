package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

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

    @Column(nullable = false, length = 20)
    private String visibility;

    @Column(nullable = false, length = 20)
    private String status;

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

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
