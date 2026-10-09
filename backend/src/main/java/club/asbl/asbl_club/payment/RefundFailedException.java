package club.asbl.asbl_club.payment;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.IdempotencyException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.StripeException;

// Stripe refused or couldn't be reached for a refund: nothing changed on our side. Two different situations for the
// person waiting for their money: Stripe out of reach for a moment (the network, Stripe's own errors, too many
// requests) is worth trying again; Stripe refusing the refund (an unknown payment, not enough balance on the
// association's account…) won't change by trying again, someone at the association has to look.
public class RefundFailedException extends RuntimeException {

    private final boolean refused;

    RefundFailedException(String message, StripeException cause) {
        super(message, cause);
        this.refused = !(cause instanceof ApiConnectionException || cause instanceof ApiException
                || cause instanceof RateLimitException || cause instanceof IdempotencyException);
    }

    // Stripe answered no: trying again won't help.
    public boolean isRefused() {
        return refused;
    }
}
