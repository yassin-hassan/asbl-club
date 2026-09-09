package club.asbl.asbl_club.payment;

/** Lifecycle of a {@link Payment}. Mirrors the {@code payments.status} check constraint. */
public enum PaymentStatus {
    INITIATED, SUCCEEDED, FAILED, REFUNDED
}
