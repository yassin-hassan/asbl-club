package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.user.User;
import com.stripe.StripeClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    StripeClient stripe;

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    RegistrationRepository registrationRepository;

    @Mock
    AuditService auditService;

    @InjectMocks
    PaymentService paymentService;

    private Payment initiatedPayment() {
        Payment payment = new Payment();
        payment.setStatus("INITIATED");
        payment.setAmount(new BigDecimal("10.00"));
        payment.setAsbl(mock(Asbl.class));
        payment.setPayable(mock(Payable.class));
        return payment;
    }

    @Test
    void handleSucceeded_marksPaidAndAuditsOnce() {
        Payment payment = initiatedPayment();
        when(paymentRepository.findByStripePaymentIntentId("pi_x")).thenReturn(Optional.of(payment));

        paymentService.handleSucceeded("pi_x");

        assertThat(payment.getStatus()).isEqualTo("SUCCEEDED");
        verify(auditService).recordSystem(eq("PAYMENT_SUCCEEDED"), any(), eq("Payment"), any(), anyMap());
    }

    @Test
    void handleSucceeded_isIdempotent_andDoesNotAuditTwice() {
        Payment payment = initiatedPayment();
        payment.setStatus("SUCCEEDED");
        when(paymentRepository.findByStripePaymentIntentId("pi_x")).thenReturn(Optional.of(payment));

        paymentService.handleSucceeded("pi_x");

        verify(auditService, never()).recordSystem(any(), any(), any(), any(), anyMap());
    }

    @Test
    void handleFailed_marksFailedAndAudits() {
        Payment payment = initiatedPayment();
        when(paymentRepository.findByStripePaymentIntentId("pi_x")).thenReturn(Optional.of(payment));

        paymentService.handleFailed("pi_x");

        assertThat(payment.getStatus()).isEqualTo("FAILED");
        verify(auditService).recordSystem(eq("PAYMENT_FAILED"), any(), eq("Payment"), any(), anyMap());
    }

    @Test
    void anonymizePaymentsOf_neutralizesPayerIdentity_keepsAmount() {
        Payment payment = new Payment();
        payment.setPayerName("Alice Dupont");
        payment.setPayerEmail("alice@club.test");
        payment.setAmount(new BigDecimal("10.00"));
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(paymentRepository.findByUser(user)).thenReturn(List.of(payment));

        paymentService.anonymizePaymentsOf(user);

        assertThat(payment.getPayerName()).doesNotContain("Alice");
        assertThat(payment.getPayerEmail()).doesNotContain("alice@club.test");
        assertThat(payment.getAmount()).isEqualByComparingTo("10.00");
    }
}
