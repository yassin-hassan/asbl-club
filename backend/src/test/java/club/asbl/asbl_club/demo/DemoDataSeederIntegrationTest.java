package club.asbl.asbl_club.demo;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataSeederIntegrationTest {

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void seedsTheDemoAssociationLinkedToStripe() {
        Asbl club = asblService.findBySlug(DemoData.MAIN.slug()).orElseThrow();
        assertThat(club.getStripeAccountId()).isEqualTo(DemoDataSeeder.DEMO_STRIPE_ACCOUNT);
        assertThat(club.getAnnualFee()).isEqualByComparingTo("30.00");
    }

    // The defence requires test data in every table.
    @Test
    void everyTableHasRows() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'""",
                String.class);
        assertThat(tables).hasSizeGreaterThanOrEqualTo(16);
        for (String table : tables) {
            Integer rows = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
            assertThat(rows).as(table).isPositive();
        }
    }

    @Test
    void theDemoLoginsWorkWithTheSharedPassword() {
        for (String email : List.of("sophie.lambert", "thomas.dubois", "julie.peeters", "admin")) {
            String hash = jdbc.queryForObject("SELECT password FROM users WHERE email = ?", String.class,
                    email + "@" + DemoData.EMAIL_DOMAIN);
            assertThat(passwordEncoder.matches(DemoDataSeeder.PASSWORD, hash)).as(email).isTrue();
        }
    }

    // Thomas buys a ticket and pays his dues live, in the demo: he must not have done so already.
    @Test
    void theDemoMemberCanStillBuyHisTicketAndPayThisYearsDues() {
        Integer concertTickets = jdbc.queryForObject("""
                SELECT count(*) FROM registrations r JOIN users u ON u.id = r.user_id JOIN events e ON e.id = r.event_id
                WHERE u.email = ? AND e.title LIKE 'Concert de gala%'""", Integer.class,
                "thomas.dubois@" + DemoData.EMAIL_DOMAIN);
        Integer duesThisYear = jdbc.queryForObject("""
                SELECT count(*) FROM dues d JOIN users u ON u.id = d.user_id
                WHERE u.email = ? AND d.year = extract(year FROM now())""", Integer.class,
                "thomas.dubois@" + DemoData.EMAIL_DOMAIN);
        assertThat(concertTickets).isZero();
        assertThat(duesThisYear).isZero();
    }

    // Unsubscribing Julie shows the soft delete on an account with payments.
    @Test
    void theMemberToUnsubscribeHasPayments() {
        Integer payments = jdbc.queryForObject("""
                SELECT count(*) FROM payments p JOIN users u ON u.id = p.user_id
                WHERE u.email = ? AND p.status = 'SUCCEEDED'""", Integer.class,
                "julie.peeters@" + DemoData.EMAIL_DOMAIN);
        assertThat(payments).isGreaterThanOrEqualTo(3);
    }

    // A closed account is anonymised, but its payments still say who paid (10-year accounting retention).
    @Test
    void aClosedAccountKeepsItsPayersIdentityOnPayments() {
        List<String> payers = jdbc.queryForList("""
                SELECT p.payer_name FROM payments p JOIN users u ON u.id = p.user_id WHERE u.deleted_at IS NOT NULL""",
                String.class);
        assertThat(payers).isNotEmpty().containsOnly("Kevin Maes");
    }

    @Test
    void seatsSoldMatchTheTicketsHeld() {
        Integer mismatches = jdbc.queryForObject("""
                SELECT count(*) FROM ticket_categories c WHERE c.sold_seats <> (SELECT count(*) FROM registrations r
                WHERE r.ticket_category_id = c.id AND r.status IN ('PAID', 'ATTENDED'))""", Integer.class);
        assertThat(mismatches).isZero();
    }

    // As the application does: a cancelled event's paid tickets are refunded (and the refund job, which would call
    // Stripe with the demo's made-up payment IDs, finds nothing to do).
    @Test
    void cancelledEventsHaveNoPaidTicketsLeft() {
        Integer paid = jdbc.queryForObject("""
                SELECT count(*) FROM registrations r JOIN events e ON e.id = r.event_id
                WHERE e.status = 'CANCELLED' AND r.status = 'PAID'""", Integer.class);
        Integer refunded = jdbc.queryForObject("""
                SELECT count(*) FROM registrations r JOIN events e ON e.id = r.event_id
                WHERE e.status = 'CANCELLED' AND r.status = 'REFUNDED'""", Integer.class);
        assertThat(paid).isZero();
        assertThat(refunded).isPositive();
    }

    @Test
    void enterpriseNumbersAreValid() {
        for (String bce : jdbc.queryForList("SELECT bce_number FROM asbls", String.class)) {
            long digits = Long.parseLong(bce.replace(".", ""));
            assertThat(97 - (digits / 100) % 97).as(bce).isEqualTo(digits % 100);
        }
    }
}
