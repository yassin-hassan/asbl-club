package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.email.EmailSender;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.stripe.StripeClient;
import com.stripe.service.RefundService;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Instant;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// After paying, the member gets their ticket by email, with the QR code attached; a late payment that is refunded
// gets an explanation instead. The emails go to GreenMail, a real SMTP server inside the test.
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "spring.mail.host=localhost",
        "spring.mail.port=3025",
        "app.public-url=https://site.test"
})
@Import(TestcontainersConfiguration.class)
@Transactional
class TicketEmailIntegrationTest {

    @RegisterExtension
    static GreenMailExtension mailServer = new GreenMailExtension(ServerSetupTest.SMTP);

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
    EmailSender outbox;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @MockitoBean
    StripeClient stripe; // refunds go to a stand-in, never to Stripe

    User bob;
    Event concert;
    Registration booking;

    @BeforeEach
    void bobBookedAndStartedPaying() {
        User admin = userService.register("Alice", "alice@club.test", "password123");
        Asbl club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        jdbcTemplate.update("UPDATE asbls SET stripe_account_id = 'acct_test' WHERE id = ?", club.getId());
        club.setStripeAccountId("acct_test");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2026-12-01T19:00:00Z"), "Hall",
                "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 10);
        eventService.publish(concert);
        bob = userService.register("Bob", "bob@club.test", "password123");
        booking = reservationService.reserve(concert, eventService.ticketCategoriesOf(concert).get(0).id(), bob);
        Payment payment = new Payment();
        payment.setAsbl(club);
        payment.setUser(bob);
        payment.setPayerName("Bob");
        payment.setPayerEmail("bob@club.test");
        payment.setPayable(booking);
        payment.setStripePaymentIntentId("pi_ticket");
        payment.setIdempotencyKey("payable-" + booking.getId());
        payment.setAmount(new BigDecimal("12.50"));
        payment.setCommission(new BigDecimal("0.68"));
        payment.setStatus(PaymentStatus.INITIATED);
        paymentRepository.save(payment);
    }

    // The text part, wherever it sits in the message's parts (Spring nests it: mixed → related → text). Narrow and
    // non-breaking spaces (French amounts: "12,50 €") read as plain spaces, for the assertions.
    private static String textOf(Part part) throws Exception {
        if (part.isMimeType("text/plain")) {
            return part.getContent().toString().replace('\u202F', ' ').replace('\u00A0', ' ');
        }
        if (part.getContent() instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String text = textOf(multipart.getBodyPart(i));
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static BodyPart attachmentOf(MimeMessage email) throws Exception {
        Multipart parts = (Multipart) email.getContent();
        for (int i = 0; i < parts.getCount(); i++) {
            if (parts.getBodyPart(i).getFileName() != null) {
                return parts.getBodyPart(i);
            }
        }
        throw new AssertionError("no attachment");
    }

    @Test
    void aPaidBooking_sendsTheTicket_withItsQrCodeAttached() throws Exception {
        jdbcTemplate.update("UPDATE users SET language = 'en' WHERE id = ?", bob.getId());
        entityManager.flush();
        entityManager.clear();

        paymentService.handleSucceeded("pi_ticket");
        outbox.sendDue();

        MimeMessage email = mailServer.getReceivedMessages()[0];
        assertThat(email.getAllRecipients()[0].toString()).isEqualTo("bob@club.test");
        assertThat(email.getSubject()).isEqualTo("Your ticket for Concert");
        String text = textOf(email);
        String code = jdbcTemplate.queryForObject("SELECT qr_token FROM registrations WHERE id = ?", String.class,
                booking.getId());
        assertThat(text).contains("Hello Bob", "Concert", "Organised by Mon Club", "December 1, 2026", "8:00",
                "Hall", "Standard", "€12.50", code.substring(0, 4) + " " + code.substring(4, 8),
                "https://site.test/bookings");

        BodyPart ticket = attachmentOf(email);
        assertThat(ticket.getFileName()).isEqualTo("ticket-" + booking.getId() + ".png");
        assertThat(ticket.getContentType()).startsWith("image/png");
        BufferedImage image = ImageIO.read(ticket.getInputStream());
        String scanned = new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image)))).getText();
        assertThat(scanned).as("the attached QR code opens the door").isEqualTo(code);

        // Sent: neither the text nor the ticket code stays in the outbox.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_outbox WHERE status = 'SENT' "
                + "AND body IS NULL AND qr_code IS NULL", Integer.class)).isEqualTo(1);
    }

    @Test
    void aPaymentRefundedBecauseTheEventWasCancelled_saysSo_inTheMembersLanguage() throws Exception {
        eventService.cancel(eventService.findEvent(concert.getAsbl(), concert.getId()).orElseThrow());
        when(stripe.refunds()).thenReturn(mock(RefundService.class));
        entityManager.flush();
        entityManager.clear();

        paymentService.handleSucceeded("pi_ticket");
        outbox.sendDue();

        MimeMessage email = mailServer.getReceivedMessages()[0];
        assertThat(email.getSubject()).isEqualTo("Remboursement : Concert"); // Bob's language: French (the default)
        assertThat(textOf(email)).contains("a été annulé", "12,50 €").doesNotContain("''");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM email_outbox WHERE qr_code IS NOT NULL "
                + "OR qr_file_name IS NOT NULL", Integer.class)).isZero(); // no ticket for a refunded booking
    }
}
