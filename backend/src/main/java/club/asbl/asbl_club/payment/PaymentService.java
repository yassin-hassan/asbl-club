package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.user.User;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.03");
    private static final BigDecimal COMMISSION_FIXED = new BigDecimal("0.30");

    private final StripeClient stripe;
    private final PaymentRepository paymentRepository;
    private final RegistrationRepository registrationRepository;
    private final AuditService auditService;

    public PaymentService(StripeClient stripe, PaymentRepository paymentRepository,
            RegistrationRepository registrationRepository, AuditService auditService) {
        this.stripe = stripe;
        this.paymentRepository = paymentRepository;
        this.registrationRepository = registrationRepository;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentInitiation initiate(Payable payable, Asbl asbl, String payerName, String payerEmail, User user)
            throws StripeException {
        if (asbl.getStripeAccountId() == null) {
            throw new IllegalStateException("The ASBL has not linked its Stripe account yet.");
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
        payment.setStatus(PaymentStatus.INITIATED);
        paymentRepository.save(payment);

        return new PaymentInitiation(payment.getId(), intent.getClientSecret());
    }

    // Stripe may send its messages more than once and in any order, so the rules only ever move a payment forward:
    // "succeeded" wins, even after "failed" (a declined card isn't the end: the person may retry on the same Stripe
    // payment with another card); "failed" only replaces "initiated", so a late one never undoes a success; and a
    // repeated message finds the work already done.
    @Transactional
    public void handleSucceeded(String paymentIntentId) {
        paymentRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(payment -> {
            if (payment.getStatus() != PaymentStatus.INITIATED && payment.getStatus() != PaymentStatus.FAILED) {
                return;
            }
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(Instant.now());
            registrationRepository.findById(payment.getPayable().getId()).ifPresent(registration -> {
                registration.setStatus(RegistrationStatus.PAID);
                registration.setQrToken(UUID.randomUUID().toString().replace("-", ""));
            });
            auditService.recordSystem("PAYMENT_SUCCEEDED", payment.getAsbl(), "Payment", payment.getId(),
                    Map.of("paymentIntentId", paymentIntentId, "amount", payment.getAmount()));
        });
    }

    @Transactional
    public void handleFailed(String paymentIntentId) {
        paymentRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.INITIATED) {
                payment.setStatus(PaymentStatus.FAILED);
                auditService.recordSystem("PAYMENT_FAILED", payment.getAsbl(), "Payment", payment.getId(),
                        Map.of("paymentIntentId", paymentIntentId));
            }
        });
    }

    @Transactional
    public void anonymizePaymentsOf(User user) {
        paymentRepository.findByUser(user).forEach(payment -> {
            payment.setPayerName("Deleted account");
            payment.setPayerEmail("deleted-" + user.getId() + "@deleted.asbl.club");
        });
    }

    @Transactional(readOnly = true)
    public List<PaymentExport> exportPaymentsOf(User user) {
        return paymentRepository.findByUser(user).stream()
                .map(payment -> new PaymentExport(payment.getAmount(), payment.getStatus().name(), payment.getPaidAt()))
                .toList();
    }

    private static BigDecimal commissionFor(BigDecimal amount) {
        return amount.multiply(COMMISSION_RATE).add(COMMISSION_FIXED).setScale(2, RoundingMode.HALF_UP);
    }

    private static long toMinorUnits(BigDecimal euros) {
        return euros.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
