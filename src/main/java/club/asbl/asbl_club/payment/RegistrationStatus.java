package club.asbl.asbl_club.payment;

/** Lifecycle of a {@link Registration}. Mirrors the {@code registrations.status} check constraint. */
public enum RegistrationStatus {
    RESERVED, PAID, CONFIRMED, ATTENDED, CANCELLED, REFUNDED, EXPIRED
}
