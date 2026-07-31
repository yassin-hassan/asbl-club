package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.audit.AuditLogView;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guards the payment finalization path. A successful payment must write a
 * PAYMENT_SUCCEEDED audit entry. This previously crashed because the audit
 * entity triggered a follow-up UPDATE that the immutable audit_logs table
 * (V8 trigger) rejects, leaving payments stuck at INITIATED with no audit.
 */
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentAuditRegressionTest {

    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    EventService eventService;
    @Autowired
    ReservationService reservationService;
    @Autowired
    PaymentService paymentService;
    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    RegistrationRepository registrationRepository;
    @Autowired
    AuditService auditService;

    @Test
    void succeededPaymentIsFinalizedAndAudited() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        Asbl club = asblService.createAsbl(alice, "Club A", "0111.111.111", "club-a", "fr");
        Event event = eventService.createEvent(club, "Concert", "Une soirée",
                Instant.parse("2026-09-01T18:00:00Z"), "Salle A", "PUBLIC");
        eventService.addTicketCategory(event, "Normal", new BigDecimal("15.00"), 100);
        Long categoryId = eventService.ticketCategoriesOf(event).get(0).id();
        Registration registration = reservationService.reserve(categoryId, alice);

        Payment payment = new Payment();
        payment.setAsbl(club);
        payment.setUser(alice);
        payment.setPayerName("Alice");
        payment.setPayerEmail("alice@club.test");
        payment.setPayable(registration);
        payment.setStripePaymentIntentId("pi_regression_1");
        payment.setIdempotencyKey("payable-" + registration.getId());
        payment.setAmount(new BigDecimal("15.00"));
        payment.setCommission(new BigDecimal("0.75"));
        payment.setStatus("INITIATED");
        paymentRepository.save(payment);

        paymentService.handleSucceeded("pi_regression_1");
        // Force the flush so any forbidden UPDATE on audit_logs would surface here.
        paymentRepository.flush();

        assertThat(paymentRepository.findByStripePaymentIntentId("pi_regression_1").orElseThrow().getStatus())
                .isEqualTo("SUCCEEDED");
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo("PAID");

        List<AuditLogView> journal = auditService.journalOf(club);
        assertThat(journal).anyMatch(entry -> "PAYMENT_SUCCEEDED".equals(entry.action()));
    }
}
