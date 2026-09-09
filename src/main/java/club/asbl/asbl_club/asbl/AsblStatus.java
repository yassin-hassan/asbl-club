package club.asbl.asbl_club.asbl;

/** Lifecycle of an {@link Asbl}. Mirrors the {@code asbls.status} check constraint. */
public enum AsblStatus {
    PENDING, ACTIVE, SUSPENDED, DELETED
}
