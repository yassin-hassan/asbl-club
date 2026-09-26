package club.asbl.asbl_club.membership;

import static org.assertj.core.api.Assertions.assertThat;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// Two administrators demote each other at the very same moment. Without the lock both would see "the other one is
// still admin" and the association would end up with none. Real, committed, concurrent transactions, so this test
// isn't @Transactional. Its data stays in the throwaway test database (unique names per run): the association
// can't be deleted anyway, since its audit entries are append-only.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@Import(TestcontainersConfiguration.class)
class LastAdminConcurrencyTest {

    @Autowired
    MembershipService membershipService;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    Asbl club;

    @Test
    void twoAdminsDemotingEachOtherAtOnce_leaveOneAdmin() throws Exception {
        // Exactly 9 random digits for this run's names (a clock value has no guaranteed length: nanoTime() counts
        // from an arbitrary origin, and was only 11 digits long on a freshly started CI machine).
        String unique = String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000));
        User first = userService.register("First", "first-" + unique + "@race.test", "password123");
        User second = userService.register("Second", "second-" + unique + "@race.test", "password123");
        club = asblService.createAsbl(first, "Race Club", "0" + unique.substring(0, 3) + "." + unique.substring(3, 6)
                + "." + unique.substring(6, 9), "race-club-" + unique, "fr");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'ADMIN', 'ACTIVE')",
                second.getId(), club.getId());

        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> refused = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<?>> runs = List.of(
                pool.submit(() -> demote(start, second, refused)),
                pool.submit(() -> demote(start, first, refused)));
        start.countDown(); // both go now
        for (Future<?> run : runs) {
            run.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Integer admins = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM memberships WHERE asbl_id = ? AND role = 'ADMIN' AND status = 'ACTIVE'",
                Integer.class, club.getId());
        assertThat(admins).isEqualTo(1);
        assertThat(refused).singleElement().isInstanceOf(LastAdminException.class);
    }

    private void demote(CountDownLatch start, User who, List<Throwable> refused) {
        try {
            start.await();
            membershipService.changeRole(club, who.getPublicId(), MembershipRole.MEMBER);
        } catch (LastAdminException e) {
            refused.add(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
