package club.asbl.asbl_club.event;

/** Publication lifecycle of an {@link Event}. Mirrors the {@code events.status} check constraint. */
public enum EventStatus {
    DRAFT, PUBLISHED, CANCELLED, ENDED
}
