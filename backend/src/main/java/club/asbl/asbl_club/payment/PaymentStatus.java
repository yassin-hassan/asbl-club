package club.asbl.asbl_club.payment;

/** Lifecycle of a {@link Payment}. Mirrors the {@code payments.status} check constraint. */
public enum PaymentStatus {
    INITIATED, SUCCEEDED, FAILED, REFUNDED,
    // Never paid, and cancelled at Stripe once its booking expired or was cancelled: it can't be paid any more.
    CANCELLED
}
