package club.asbl.asbl_club.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import club.asbl.asbl_club.membership.MembershipRole;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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
import org.springframework.transaction.annotation.Transactional;

// An association's finances for its treasurer: a year's money in, refunds (the commission going back or not, as the
// refund decided), the platform's commission, and every payment, on screen and as a spreadsheet. Payments are written
// straight into the database, as the payment flow (tested on its own) leaves them.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class FinanceIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    MembershipService membershipService;
    @Autowired
    EventService eventService;
    @Autowired
    FinanceService financeService;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;

    Asbl club;
    User bob;
    User carol;
    long concert;
    long standard;

    @BeforeEach
    void aYearOfPayments() {
        User alice = userService.register("Alice", "alice@club.test", "password123");
        club = asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        bob = userService.register("Bob", "bob@club.test", "password123");
        membershipService.joinByInvitation(bob, club);
        carol = userService.register("Carol", "carol@club.test", "password123");
        User dave = userService.register("Dave", "dave@club.test", "password123");
        membershipService.joinByInvitation(dave, club);
        membershipService.changeRole(club, dave.getPublicId(), MembershipRole.TREASURER);
        Event event = eventService.createEvent(club, "Concert", null, Instant.parse("2030-06-01T19:00:00Z"), "Hall",
                "PUBLIC");
        eventService.addTicketCategory(event, "Standard", new BigDecimal("12.00"), 100);
        concert = event.getId();
        standard = eventService.ticketCategoriesOf(event).get(0).id();
        entityManager.flush();

        ticket(club, bob, "Bob", "bob@club.test", "SUCCEEDED", "2030-03-01T10:00:00Z", null, false);
        // Cancelled by the buyer: refunded, the platform keeping its commission.
        ticket(club, carol, "Carol", "carol@club.test", "REFUNDED", "2030-03-02T10:00:00Z", "2030-03-05T10:00:00Z",
                false);
        // A guest whose event was cancelled: refunded, the commission too.
        ticket(club, null, "Gaston", "gaston@mail.test", "REFUNDED", "2030-03-03T10:00:00Z", "2030-03-04T10:00:00Z",
                true);
        dues(club, bob, 2030, "2030-01-10T10:00:00Z");
        // Half past midnight on New Year's Day in Brussels: 2030, though still 2029 in UTC.
        ticket(club, carol, "Carol", "carol@club.test", "SUCCEEDED", "2029-12-31T23:30:00Z", null, false);
        // Not counted: another year, a payment that never went through.
        ticket(club, bob, "Bob", "bob@club.test", "SUCCEEDED", "2029-06-01T10:00:00Z", null, false);
        ticket(club, bob, "Bob", "bob@club.test", "INITIATED", null, null, false);
    }

    @Test
    void theTreasurer_seesTheYearsTotals_andItsPayments() throws Exception {
        List<Integer> years = Stream.of(2030, 2029, financeService.currentYear()).distinct()
                .sorted(Comparator.reverseOrder()).toList();
        report("dave", "?year=2030").andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2030))
                .andExpect(jsonPath("$.years").value(contains(years.toArray())))
                .andExpect(jsonPath("$.totals.payments").value(5))
                .andExpect(jsonPath("$.totals.tickets").value(48.00))
                .andExpect(jsonPath("$.totals.dues").value(25.00))
                .andExpect(jsonPath("$.totals.collected").value(73.00))
                .andExpect(jsonPath("$.totals.refunded").value(24.00))
                // Bob's ticket and dues, Carol's two tickets (one refunded, its commission kept); not Gaston's.
                .andExpect(jsonPath("$.totals.commission").value(3.03))
                .andExpect(jsonPath("$.totals.net").value(45.97))
                .andExpect(jsonPath("$.payments[*].payerName")
                        .value(contains("Gaston", "Carol", "Bob", "Bob", "Carol")))
                .andExpect(jsonPath("$.payments[0].kind").value("TICKET"))
                .andExpect(jsonPath("$.payments[0].eventTitle").value("Concert"))
                .andExpect(jsonPath("$.payments[0].ticketLabel").value("Standard"))
                .andExpect(jsonPath("$.payments[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.payments[0].commission").value(0.00))
                .andExpect(jsonPath("$.payments[0].refundedAt").value("2030-03-04T10:00:00Z"))
                .andExpect(jsonPath("$.payments[1].commission").value(0.66))
                .andExpect(jsonPath("$.payments[3].kind").value("DUES"))
                .andExpect(jsonPath("$.payments[3].duesYear").value(2030))
                .andExpect(jsonPath("$.payments[3].status").value("PAID"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalPages").value(1));

        report("dave", "?year=2029").andExpect(jsonPath("$.totals.payments").value(1))
                .andExpect(jsonPath("$.totals.net").value(11.34));
        report("dave", "?year=2030&page=1").andExpect(jsonPath("$.payments.length()").value(0));
    }

    // By default, the current year; nothing in it yet is an answer too.
    @Test
    void withoutAYear_theCurrentOne() throws Exception {
        report("alice", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(financeService.currentYear()))
                .andExpect(jsonPath("$.totals.payments").value(0))
                .andExpect(jsonPath("$.totals.net").value(0));
    }

    @Test
    void plainMembersAndOutsiders_cannotSeeTheFinances() throws Exception {
        report("bob", "").andExpect(status().isForbidden());
        report("carol", "").andExpect(status().isForbidden());
        export("bob", "en").andExpect(status().isForbidden());
    }

    // Another association's administrator sees neither this one's finances nor its payments in their own.
    @Test
    void anotherAssociation_seesNothingOfThisOne() throws Exception {
        asblService.createAsbl(userService.register("Erin", "erin@club.test", "password123"), "Autre Club",
                "0987.654.321", "autre-club", "fr");
        report("erin", "?year=2030").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/asbls/autre-club/manage/finances?year=2030")
                        .header("Authorization", "Bearer " + tokenFor("erin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.payments").value(0))
                .andExpect(jsonPath("$.payments.length()").value(0));
    }

    @Test
    void aYearOutOfRange_isABadRequest() throws Exception {
        report("dave", "?year=1999").andExpect(status().isBadRequest());
        report("dave", "?page=-1").andExpect(status().isBadRequest());
    }

    // The spreadsheet for the accounts: every payment of the year; the download audited (personal data).
    @Test
    void theTreasurer_downloadsTheYear_asASpreadsheet() throws Exception {
        String text = new String(export("dave", "en").andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"finances-mon-club-2030.csv\""))
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(text)
                .startsWith("﻿Date;Payer;Email;Type;Description;Amount (EUR);Platform commission (EUR);Status;"
                        + "Refunded at\r\n")
                .contains("2030-03-03 11:00;Gaston;gaston@mail.test;Ticket;Concert — Standard;12.00;0.00;Refunded;"
                        + "2030-03-04 11:00\r\n")
                .contains("2030-01-10 11:00;Bob;bob@club.test;Dues;Dues 2030;25.00;1.05;Paid;\r\n")
                .contains("2030-01-01 00:30;Carol;carol@club.test;Ticket;Concert — Standard;12.00;0.66;Paid;\r\n");
        assertThat(text.lines().count()).isEqualTo(6);
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT payload ->> 'year' FROM audit_logs "
                + "WHERE action = 'FINANCES_EXPORTED'", String.class)).isEqualTo("2030");
    }

    private void ticket(Asbl asbl, User buyer, String name, String email, String status, String paidAt,
            String refundedAt, boolean commissionRefunded) {
        long payable = payable("REGISTRATION", "12.00");
        jdbcTemplate.update("""
                INSERT INTO registrations (id, event_id, ticket_category_id, user_id, guest_email, guest_name,
                guest_language, access_token_hash, status) VALUES (?, ?, ?, ?, ?, ?, 'fr', ?, ?)""",
                payable, concert, standard, buyer == null ? null : buyer.getId(), buyer == null ? email : null,
                buyer == null ? name : null, buyer == null ? "hash-" + payable : null,
                status.equals("REFUNDED") ? "REFUNDED" : "PAID");
        payment(asbl, buyer, name, email, payable, "12.00", "0.66", status, paidAt, refundedAt, commissionRefunded);
    }

    private void dues(Asbl asbl, User member, int year, String paidAt) {
        long payable = payable("MEMBERSHIP", "25.00");
        jdbcTemplate.update("INSERT INTO dues (id, asbl_id, user_id, year) VALUES (?, ?, ?, ?)", payable,
                asbl.getId(), member.getId(), year);
        payment(asbl, member, member.getName(), member.getEmail(), payable, "25.00", "1.05", "SUCCEEDED", paidAt,
                null, false);
    }

    private long payable(String type, String amount) {
        return jdbcTemplate.queryForObject("INSERT INTO payables (type, amount) VALUES (?, ?) RETURNING id",
                Long.class, type, new BigDecimal(amount));
    }

    private void payment(Asbl asbl, User payer, String name, String email, long payable, String amount,
            String commission, String status, String paidAt, String refundedAt, boolean commissionRefunded) {
        jdbcTemplate.update("""
                INSERT INTO payments (asbl_id, user_id, payer_name, payer_email, payable_id, amount, commission, status,
                paid_at, refunded_at, commission_refunded) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                asbl.getId(), payer == null ? null : payer.getId(), name, email, payable, new BigDecimal(amount),
                new BigDecimal(commission), status, timestamp(paidAt), timestamp(refundedAt), commissionRefunded);
    }

    private static Timestamp timestamp(String instant) {
        return instant == null ? null : Timestamp.from(Instant.parse(instant));
    }

    private ResultActions report(String who, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/asbls/mon-club/manage/finances" + query)
                .header("Authorization", "Bearer " + tokenFor(who)));
    }

    private ResultActions export(String who, String language) throws Exception {
        return mockMvc.perform(get("/api/v1/asbls/mon-club/manage/finances/export?year=2030")
                .header("Authorization", "Bearer " + tokenFor(who)).header("Accept-Language", language));
    }

    private String tokenFor(String who) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s@club.test\", \"password\": \"password123\"}".formatted(who)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
