package club.asbl.asbl_club.payment;

// Stripe refused or couldn't be reached for a refund: nothing changed on our side, it can be tried again.
public class RefundFailedException extends RuntimeException {

    RefundFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
