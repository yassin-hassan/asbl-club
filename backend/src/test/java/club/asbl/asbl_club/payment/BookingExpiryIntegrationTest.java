package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.event.TicketSoldOutException;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// An unpaid booking gives its seat back after a while (30 minutes in production).
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class BookingExpiryIntegrationTest {

    private static final Duration WINDOW = Duration.ofMinutes(30);

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
    BookingExpiry bookingExpiry;
    @Autowired
    RegistrationRepository registrationRepository;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;

    User alice;
    Asbl club;
    Event concert;
    Long standard;

    @BeforeEach
    void aPublishedConcert() {
        alice = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 10);
        standard = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
    }

    @Test
    void anOverdueUnpaidBooking_expires_andGivesItsSeatBack() {
        Registration overdue = bookedMinutesAgo(31);
        Registration recent = bookedMinutesAgo(5);
        Registration paid = bookedMinutesAgo(45);
        jdbcTemplate.update("UPDATE registrations SET status = 'PAID' WHERE id = ?", paid.getId());

        int expired = bookingExpiry.expireReservedBefore(Instant.now().minus(WINDOW));

        assertThat(expired).isEqualTo(1);
        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
        assertThat(statusOf(recent)).isEqualTo("RESERVED");
        assertThat(statusOf(paid)).isEqualTo("PAID");
        assertThat(soldSeats()).isEqualTo(2); // 3 booked, 1 given back
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE action = 'BOOKING_EXPIRED' AND entity_id = ?",
                Integer.class, overdue.getId())).isEqualTo(1);
    }

    @Test
    void theLastSeat_givenBack_canBeBookedAgain() {
        Long single = singleSeatCategory();
        Registration abandoned = reservationService.reserve(concert, single, alice);
        makeOld(abandoned, 31);
        assertThatThrownBy(() -> reservationService.reserve(concert, single, alice))
                .isInstanceOf(TicketSoldOutException.class);

        bookingExpiry.expireReservedBefore(Instant.now().minus(WINDOW));

        assertThat(reservationService.reserve(concert, single, alice).getStatus()).isEqualTo(RegistrationStatus.RESERVED);
    }

    @Test
    void anExpiredBooking_cannotBePaid() throws Exception {
        Registration expired = bookedMinutesAgo(31);
        bookingExpiry.expireReservedBefore(Instant.now().minus(WINDOW));

        mockMvc.perform(post("/api/v1/registrations/" + expired.getId() + "/checkout")
                        .header("Authorization", "Bearer " + tokenFor("alice@club.test")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_EXPIRED"));
    }

    @Test
    void runningTwice_expiresOnce() {
        Registration overdue = bookedMinutesAgo(31);

        bookingExpiry.expireReservedBefore(Instant.now().minus(WINDOW));
        bookingExpiry.expireReservedBefore(Instant.now().minus(WINDOW));

        assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
        assertThat(soldSeats()).isZero(); // the seat is given back once, not twice
    }

    private Registration bookedMinutesAgo(int minutes) {
        Registration booking = reservationService.reserve(concert, standard, alice);
        makeOld(booking, minutes);
        return booking;
    }

    private void makeOld(Registration booking, int minutes) {
        entityManager.flush();
        jdbcTemplate.update("UPDATE registrations SET registered_at = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofMinutes(minutes))), booking.getId());
    }

    private Long singleSeatCategory() {
        eventService.addTicketCategory(concert, "Last one", new BigDecimal("5.00"), 1);
        return eventService.ticketCategoriesOf(concert).stream()
                .filter(t -> t.label().equals("Last one")).findFirst().orElseThrow().id();
    }

    private String statusOf(Registration booking) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM registrations WHERE id = ?", String.class,
                booking.getId());
    }

    private int soldSeats() {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT sold_seats FROM ticket_categories WHERE id = ?", Integer.class,
                standard);
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
