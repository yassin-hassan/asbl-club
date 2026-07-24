package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.03");
    private static final BigDecimal COMMISSION_FIXED = new BigDecimal("0.30");

    private final StripeClient stripe;
    private final PaymentRepository paymentRepository;

    public PaymentService(StripeClient stripe, PaymentRepository paymentRepository) {
        this.stripe = stripe;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentInitiation initiate(Payable payable, Asbl asbl, String payerName, String payerEmail, User user)
            throws StripeException {
        if (asbl.getStripeAccountId() == null) {
            throw new IllegalStateException("L'ASBL n'a pas encore relié son compte Stripe.");
        }

        RequestOptions connectedAccount = RequestOptions.builder()
                .setStripeAccount(asbl.getStripeAccountId())
                .build();

        Optional<Payment> existing = paymentRepository.findByPayableId(payable.getId());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            PaymentIntent intent = stripe.paymentIntents()
                    .retrieve(payment.getStripePaymentIntentId(), connectedAccount);
            return new PaymentInitiation(payment.getId(), intent.getClientSecret());
        }

        BigDecimal commission = commissionFor(payable.getAmount());
        String currency = payable.getCurrency().toLowerCase();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toMinorUnits(payable.getAmount()))
                .setCurrency(currency)
                .setApplicationFeeAmount(toMinorUnits(commission))
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        RequestOptions options = RequestOptions.builder()
                .setStripeAccount(asbl.getStripeAccountId())
                .setIdempotencyKey("payable-" + payable.getId())
                .build();

        PaymentIntent intent = stripe.paymentIntents().create(params, options);

        Payment payment = new Payment();
        payment.setAsbl(asbl);
        payment.setUser(user);
        payment.setPayerName(payerName);
        payment.setPayerEmail(payerEmail);
        payment.setPayable(payable);
        payment.setStripePaymentIntentId(intent.getId());
        payment.setIdempotencyKey("payable-" + payable.getId());
        payment.setAmount(payable.getAmount());
        payment.setCommission(commission);
        payment.setStatus("INITIATED");
        paymentRepository.save(payment);

        return new PaymentInitiation(payment.getId(), intent.getClientSecret());
    }

    private static BigDecimal commissionFor(BigDecimal amount) {
        return amount.multiply(COMMISSION_RATE).add(COMMISSION_FIXED).setScale(2, RoundingMode.HALF_UP);
    }

    private static long toMinorUnits(BigDecimal euros) {
        return euros.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
