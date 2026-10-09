package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.RefundService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// An association cancels an event: its paid tickets are refunded in full (commission included), each buyer is told
// by email, and a refund Stripe refuses is tried again later, never twice. Stripe is a stand-in.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
@Transactional
class CancelledEventRefundsIntegrationTest {

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
    CancelledEventRefunds refunds;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @MockitoBean
    StripeClient stripe;

    RefundService refundService = mock(RefundService.class);
    Asbl club;
    Event concert;
    Registration ticket;
    Payment payment;

    @BeforeEach
    void bobPaidForATicket() {
        when(stripe.refunds()).thenReturn(refundService);
        User admin = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        asblService.linkStripeAccount(club, "acct_test");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2030-12-01T19:00:00Z"), "Hall",
                "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 10);
        eventService.publish(concert);
        User bob = userService.register("Bob", "bob@club.test", "password123");
        ticket = reservationService.reserve(concert, eventService.ticketCategoriesOf(concert).get(0).id(), bob);
        payment = new Payment();
        payment.setAsbl(club);
        payment.setUser(bob);
        payment.setPayerName("Bob");
        payment.setPayerEmail("bob@club.test");
        payment.setPayable(ticket);
        payment.setStripePaymentIntentId("pi_paid");
        payment.setIdempotencyKey("payable-" + ticket.getId());
        payment.setAmount(new BigDecimal("12.50"));
        payment.setCommission(new BigDecimal("0.68"));
        payment.setStatus(PaymentStatus.INITIATED);
        paymentRepository.save(payment);
        paymentService.handleSucceeded("pi_paid");
        entityManager.flush();
    }

    @Test
    void cancellingAnEvent_refundsItsPaidTickets_inFull_andTellsTheBuyer() throws Exception {
        eventService.cancel(concert);
        entityManager.flush();

        assertThat(refunds.refundAll()).isEqualTo(1);

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(refundService).create(params.capture(), options.capture());
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_paid");
        assertThat(params.getValue().getRefundApplicationFee()).as("the commission goes back too").isTrue();
        assertThat(options.getValue().getStripeAccount()).isEqualTo("acct_test");
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("refund-" + payment.getId());

        assertThat(status("registrations", ticket.getId())).isEqualTo("REFUNDED");
        assertThat(status("payments", payment.getId())).isEqualTo("REFUNDED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'PAYMENT_REFUNDED' "
                + "AND payload ->> 'reason' = 'event cancelled'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM email_outbox WHERE recipient = 'bob@club.test' "
                + "AND subject LIKE 'Événement annulé%'", String.class)).isEqualTo("Événement annulé : Concert");
    }

    @Test
    void aRefundStripeRefuses_leavesTheTicketPaid_andIsTriedAgain_butNeverTwice() throws Exception {
        eventService.cancel(concert);
        entityManager.flush();
        when(refundService.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("Stripe is down"))
                .thenReturn(new Refund());

        assertThat(refunds.refundAll()).isZero();
        assertThat(status("registrations", ticket.getId())).isEqualTo("PAID");

        assertThat(refunds.refundAll()).isEqualTo(1);
        assertThat(refunds.refundAll()).as("nothing left to refund").isZero();
        verify(refundService, times(2)).create(any(RefundCreateParams.class), any(RequestOptions.class));
        assertThat(status("registrations", ticket.getId())).isEqualTo("REFUNDED");
    }

    @Test
    void ticketsOfEventsStillOn_areNotRefunded() throws Exception {
        assertThat(refunds.refundAll()).isZero();
        verify(refundService, never()).create(any(RefundCreateParams.class), any(RequestOptions.class));
        assertThat(status("registrations", ticket.getId())).isEqualTo("PAID");
    }

    private String status(String table, Long id) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }
}
