package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.payment.Registrations.Checkout;
import club.asbl.asbl_club.user.User;
import com.stripe.exception.StripeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

// Starting (or resuming) the payment of a booking, the same for an account holder and for a guest: only who pays
// differs. Checks first that there is something to pay and somewhere for the money to go.
@Component
class BookingCheckout {

    private static final Logger log = LoggerFactory.getLogger(BookingCheckout.class);

    private final PaymentService paymentService;
    private final StripeProperties stripeProperties;

    BookingCheckout(PaymentService paymentService, StripeProperties stripeProperties) {
        this.paymentService = paymentService;
        this.stripeProperties = stripeProperties;
    }

    // user: the account paying, or null for a guest.
    Checkout start(Registration registration, String payerName, String payerEmail, User user) {
        if (registration.getStatus() == RegistrationStatus.EXPIRED) {
            throw conflict("BOOKING_EXPIRED", "This booking wasn't paid in time; its seat was given back.");
        }
        if (registration.getStatus() != RegistrationStatus.RESERVED) {
            throw conflict("NOTHING_TO_PAY", "This booking has nothing left to pay.");
        }
        Asbl asbl = registration.getEvent().getAsbl();
        requirePayments(asbl);
        try {
            PaymentInitiation initiation = paymentService.initiate(registration, asbl, payerName, payerEmail, user);
            return new Checkout(stripeProperties.publishableKey(), asbl.getStripeAccountId(),
                    initiation.clientSecret(), registration.getAmount(), registration.getCurrency());
        } catch (StripeException e) {
            log.warn("Stripe refused or failed to start a payment for registration {}: {}", registration.getId(),
                    e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider couldn't be reached.");
        }
    }

    // The buyer cancels their paid ticket (account holder or guest): refunded, or a stable reason why not.
    void cancel(Registration registration) {
        try {
            paymentService.cancelByBuyer(registration.getId());
        } catch (NotCancellableException e) {
            throw conflict("NOT_CANCELLABLE", "Only a paid, unused ticket of an event still on can be cancelled.");
        } catch (CancellationClosedException e) {
            throw conflict("CANCELLATION_CLOSED", "This ticket can't be cancelled any more (or never could).");
        } catch (RefundFailedException e) {
            log.warn("Refund of cancelled ticket {} failed: {}", registration.getId(), e.getCause().getMessage());
            if (e.isRefused()) {
                // Stripe answered no: trying again won't help; the association has to look at its Stripe account.
                ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                        "The payment provider refused the refund.");
                problem.setProperty("code", "REFUND_REFUSED");
                throw new ErrorResponseException(HttpStatus.BAD_GATEWAY, problem, null);
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The payment provider couldn't be reached.");
        }
    }

    static void requirePayments(Asbl asbl) {
        if (asbl.getStripeAccountId() == null) {
            throw conflict("PAYMENTS_DISABLED", "This association can't receive payments yet.");
        }
    }

    // Several different conflicts share status 409: a stable "code" tells clients which one, independent of the
    // human-readable (and changeable) detail text.
    static ErrorResponseException conflict(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }
}
