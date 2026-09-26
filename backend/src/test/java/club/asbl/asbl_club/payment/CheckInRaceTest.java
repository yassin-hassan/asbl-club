package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

// Two people at the door scan the same ticket (or a copy of it) at the same instant: exactly one lets someone in.
// Real, committed, concurrent transactions, so not @Transactional; data stays under unique names.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckInRaceTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    EventService eventService;
    @Autowired
    ReservationService reservationService;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void twoScansOfOneTicketAtOnce_letOnePersonIn() throws Exception {
        String unique = String.valueOf(ThreadLocalRandom.current().nextInt(100_000_000, 1_000_000_000));
        User admin = userService.register("Door", "door-" + unique + "@race.test", "password123");
        Asbl club = asblService.createAsbl(admin, "Door Club", "0" + unique.substring(0, 3) + "."
                + unique.substring(3, 6) + "." + unique.substring(6, 9), "door-club-" + unique, "fr");
        Event event = eventService.createEvent(club, "Gala", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(event, "Standard", new BigDecimal("10.00"), 100);
        eventService.publish(event);
        Long category = eventService.ticketCategoriesOf(event).get(0).id();
        String token = tokenFor(admin.getEmail());

        for (int round = 0; round < 10; round++) {
            Registration ticket = reservationService.reserve(event, category, admin);
            String code = "%032x".formatted(ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE);
            jdbcTemplate.update("UPDATE registrations SET status = 'PAID', qr_token = ? WHERE id = ?", code,
                    ticket.getId());

            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Future<String>> scans = new ArrayList<>();
            for (int door = 0; door < 2; door++) {
                scans.add(pool.submit(() -> {
                    start.await();
                    String body = mockMvc.perform(post("/api/v1/asbls/door-club-" + unique + "/manage/events/"
                                    + event.getId() + "/check-ins")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + code + "\"}"))
                            .andReturn().getResponse().getContentAsString();
                    return JsonPath.read(body, "$.outcome");
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> scan : scans) {
                outcomes.add(scan.get(30, TimeUnit.SECONDS));
            }
            pool.shutdown();

            assertThat(outcomes).containsExactlyInAnyOrder("CHECKED_IN", "ALREADY_CHECKED_IN");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM audit_logs WHERE action = 'TICKET_CHECKED_IN' AND entity_id = ?",
                    Integer.class, ticket.getId())).isEqualTo(1);
        }
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
