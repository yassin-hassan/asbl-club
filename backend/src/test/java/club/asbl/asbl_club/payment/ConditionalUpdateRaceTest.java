package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// The "only if still reserved" updates must hold when another transaction changes the booking while they wait for
// its row lock. Deterministic, not left to timing: transaction A marks the booking paid and holds the lock; the
// update under test starts and blocks on it (Postgres says so); A commits; the update must then change nothing.
// (Hibernate's bulk updates on a joined-inheritance entity failed exactly this: select the ids first, update later.)
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
class ConditionalUpdateRaceTest {

    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    EventService eventService;
    @Autowired
    ReservationService reservationService;
    @Autowired
    RegistrationRepository registrationRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transaction;

    Event event;
    Registration booking;

    @BeforeEach
    void aReservedBooking() {
        String unique = String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000));
        User member = userService.register("Payer", "payer-" + unique + "@race.test", "password123");
        Asbl club = asblService.createAsbl(member, "Race Club", "0" + unique.substring(0, 3) + "."
                + unique.substring(3, 6) + "." + unique.substring(6, 9), "race-" + unique, "fr");
        event = eventService.createEvent(club, "Gala", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(event, "Standard", new BigDecimal("10.00"), 10);
        eventService.publish(event);
        booking = reservationService.reserve(event, eventService.ticketCategoriesOf(event).get(0).id(), member);
    }

    @Test
    void expiry_doesNotOverwriteAPaymentCommittedWhileItWaited() throws Exception {
        int changed = whilePaymentHoldsTheRow(() -> registrationRepository.expireIfReserved(booking.getId()));

        assertThat(changed).isZero();
        assertThat(status()).isEqualTo("PAID");
    }

    @Test
    void eventCancellation_doesNotOverwriteAPaymentCommittedWhileItWaited() throws Exception {
        int changed = whilePaymentHoldsTheRow(() -> registrationRepository.cancelUnpaid(event.getId()));

        assertThat(changed).isZero();
        assertThat(status()).isEqualTo("PAID");
    }

    private int whilePaymentHoldsTheRow(Supplier<Integer> update) throws Exception {
        CountDownLatch paidButNotCommitted = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<?> payment = pool.submit(() -> transaction.executeWithoutResult(status -> {
            jdbcTemplate.update("UPDATE registrations SET status = 'PAID' WHERE id = ?", booking.getId());
            paidButNotCommitted.countDown();
            await(commit);
        }));
        await(paidButNotCommitted);
        Future<Integer> contender = pool.submit(() -> transaction.execute(status -> update.get()));
        waitUntilSomeoneWaitsForALock();
        commit.countDown();
        payment.get(30, TimeUnit.SECONDS);
        int changed = contender.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        return changed;
    }

    private void waitUntilSomeoneWaitsForALock() throws InterruptedException {
        for (int i = 0; i < 300; i++) {
            Integer waiting = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("The update under test never waited for the row lock");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private String status() {
        return jdbcTemplate.queryForObject("SELECT status FROM registrations WHERE id = ?", String.class,
                booking.getId());
    }
}
