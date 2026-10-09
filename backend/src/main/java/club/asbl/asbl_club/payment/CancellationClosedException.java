package club.asbl.asbl_club.payment;

// The ticket's event doesn't allow cancelling on request, or its cancellation delay has passed.
public class CancellationClosedException extends RuntimeException {
}
