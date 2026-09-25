package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.event.Event;
import club.asbl.asbl_club.event.EventService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import com.stripe.exception.ApiConnectionException;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// Stripe itself is replaced by a stand-in (it's an external service with real keys); everything else is real.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class RegistrationApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    EventService eventService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    PaymentService paymentService;

    Asbl club;
    Event concert;
    Long standardTicket;
    String aliceToken;
    String bobToken;

    @BeforeEach
    void aPublishedEventAndTwoMembersOfItsAssociation() throws Exception {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 1);
        standardTicket = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
        userService.register("Bob", "bob@club.test", "password123");
        aliceToken = tokenFor("alice@club.test");
        bobToken = tokenFor("bob@club.test");
    }

    @Test
    void aMember_booksASeat_andOnlyTheyCanSeeTheBooking() throws Exception {
        String body = book(aliceToken, "mon-club", concert.getId(), standardTicket)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.amount").value(12.50))
                .andExpect(jsonPath("$.ticketLabel").value("Standard"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        mockMvc.perform(get("/api/v1/registrations/" + id).header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/registrations/" + id).header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        assertThat(soldSeats(standardTicket)).isEqualTo(1);
    }

    @Test
    void theLastSeat_cannotBeBookedTwice() throws Exception {
        book(aliceToken, "mon-club", concert.getId(), standardTicket).andExpect(status().isCreated());
        book(aliceToken, "mon-club", concert.getId(), standardTicket).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOLD_OUT"));
    }

    @Test
    void outsiders_cannotBook() throws Exception {
        book(bobToken, "mon-club", concert.getId(), standardTicket).andExpect(status().isForbidden());
    }

    @Test
    void drafts_cannotBeBooked() throws Exception {
        Event draft = eventService.createEvent(club, "Draft", null, Instant.parse("2026-12-02T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(draft, "Standard", new BigDecimal("5.00"), 10);
        Long draftTicket = eventService.ticketCategoriesOf(draft).get(0).id();

        book(aliceToken, "mon-club", draft.getId(), draftTicket).andExpect(status().isConflict());
    }

    // IDOR: a ticket of another association's event, booked through an event the caller is a member of.
    @Test
    void aTicketOfAnotherEvent_cannotBeBookedThroughThisOne() throws Exception {
        User carol = userService.register("Carol", "carol@club.test", "password123");
        Asbl otherClub = asblService.createAsbl(carol, "Other", "0987.654.321", "other-club", "fr");
        Event otherEvent = eventService.createEvent(otherClub, "Other", null, Instant.parse("2026-12-03T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(otherEvent, "Theirs", new BigDecimal("1.00"), 10);
        eventService.publish(otherEvent);
        Long theirTicket = eventService.ticketCategoriesOf(otherEvent).get(0).id();

        book(aliceToken, "mon-club", concert.getId(), theirTicket).andExpect(status().isNotFound());
        assertThat(soldSeats(theirTicket)).isZero(); // no seat taken
    }

    @Test
    void checkout_givesTheOwnerWhatStripeNeeds() throws Exception {
        asblService.linkStripeAccount(club, "acct_test123");
        when(paymentService.initiate(any(), any(), any(), any(), any())).thenReturn(new PaymentInitiation(1L, "pi_secret_abc"));
        Integer id = bookedId();

        checkout(aliceToken, id).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_secret_abc"))
                .andExpect(jsonPath("$.stripeAccount").value("acct_test123"))
                .andExpect(jsonPath("$.amount").value(12.50));
        checkout(bobToken, id).andExpect(status().isNotFound());
    }

    @Test
    void checkout_withoutStripeConnected_isAConflict() throws Exception {
        checkout(aliceToken, bookedId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENTS_DISABLED"));
    }

    @Test
    void checkout_whenStripeIsUnreachable_isABadGateway() throws Exception {
        asblService.linkStripeAccount(club, "acct_test123");
        when(paymentService.initiate(any(), any(), any(), any(), any()))
                .thenThrow(new ApiConnectionException("network down"));

        checkout(aliceToken, bookedId()).andExpect(status().isBadGateway());
    }

    // Straight from the database: taking a seat is a direct UPDATE that bypasses Hibernate's cached copy.
    private Integer soldSeats(Long ticketCategoryId) {
        return jdbcTemplate.queryForObject("SELECT sold_seats FROM ticket_categories WHERE id = ?", Integer.class,
                ticketCategoryId);
    }

    private Integer bookedId() throws Exception {
        return JsonPath.read(book(aliceToken, "mon-club", concert.getId(), standardTicket)
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions book(String token, String slug, Long eventId, Long ticketId) throws Exception {
        return mockMvc.perform(post("/api/v1/asbls/" + slug + "/manage/events/" + eventId + "/registrations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ticketCategoryId\": " + ticketId + "}"));
    }

    private ResultActions checkout(String token, Integer id) throws Exception {
        return mockMvc.perform(post("/api/v1/registrations/" + id + "/checkout").header("Authorization", "Bearer " + token));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
