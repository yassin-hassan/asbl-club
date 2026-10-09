package club.asbl.asbl_club.payment;

// Only a paid ticket of an event still on can be cancelled: not one unpaid, already used at the door, or refunded.
public class NotCancellableException extends RuntimeException {
}
