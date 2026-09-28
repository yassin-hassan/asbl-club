package club.asbl.asbl_club.dues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.membership.MembershipRole;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.payment.StripeWebhooks;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.service.PaymentIntentService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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

// Membership dues end to end: the administrator sets the fee, a member pays it through the ticket payment flow
// (Stripe's API replaced by a stand-in; its webhook signed as Stripe signs it), and gets a receipt.
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "stripe.webhook-secret=" + StripeWebhooks.SECRET
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class DuesIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserService userService;
    @Autowired
    AsblService asblService;
    @Autowired
    MembershipService membershipService;
    @Autowired
    DuesService duesService;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    EntityManager entityManager;
    @MockitoBean
    StripeClient stripe;

    PaymentIntentService intents = mock(PaymentIntentService.class);
    Asbl club;
    String aliceToken; // administrator
    String bobToken; // member
    String carolToken; // outsider

    @BeforeEach
    void anAssociationWithAMemberAndStripe() throws Exception {
        club = asblService.createAsbl(userService.register("Alice", "alice@club.test", "password123"), "Mon Club",
                "0123.456.789", "mon-club", "fr");
        membershipService.joinByInvitation(userService.register("Bob", "bob@club.test", "password123"), club);
        userService.register("Carol", "carol@club.test", "password123");
        aliceToken = tokenFor("alice@club.test");
        bobToken = tokenFor("bob@club.test");
        carolToken = tokenFor("carol@club.test");

        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_dues_1");
        intent.setClientSecret("pi_dues_1_secret_abc");
        when(stripe.paymentIntents()).thenReturn(intents);
        when(intents.create(any(PaymentIntentCreateParams.class), any(RequestOptions.class))).thenReturn(intent);
        when(intents.retrieve(eq("pi_dues_1"), any(RequestOptions.class))).thenReturn(intent);
    }

    @Test
    void theAdministratorSetsTheFee_aMemberPaysIt_andGetsAReceipt() throws Exception {
        asblService.linkStripeAccount(club, "acct_club");
        setFee(aliceToken, "25.00").andExpect(status().isNoContent());
        int year = duesService.currentYear();

        myDues(bobToken).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("mon-club"))
                .andExpect(jsonPath("$[0].year").value(year))
                .andExpect(jsonPath("$[0].amount").value(25.00))
                .andExpect(jsonPath("$[0].paid").value(false));

        checkout(bobToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_dues_1_secret_abc"))
                .andExpect(jsonPath("$.stripeAccount").value("acct_club"))
                .andExpect(jsonPath("$.amount").value(25.00));
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT p.commission FROM payments p JOIN payables d ON d.id = p.payable_id "
                + "WHERE d.type = 'MEMBERSHIP'", BigDecimal.class)).isEqualByComparingTo("1.05"); // 3 % + 0.30

        // Stripe confirms the payment (the browser's redirect alone proves nothing).
        mockMvc.perform(StripeWebhooks.signed("evt_dues_1", "payment_intent.succeeded", "pi_dues_1"))
                .andExpect(status().isOk());

        myDues(bobToken).andExpect(jsonPath("$[0].paid").value(true))
                .andExpect(jsonPath("$[0].paidAt").exists());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT subject FROM email_outbox WHERE recipient = 'bob@club.test'",
                String.class)).isEqualTo("Cotisation " + year + " payée : Mon Club");
        checkout(bobToken).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_PAID"));
    }

    // Two tabs, or coming back after closing Stripe's form: the same dues, the same Stripe payment.
    @Test
    void payingAgainBeforeItWentThrough_resumesTheSamePayment() throws Exception {
        asblService.linkStripeAccount(club, "acct_club");
        setFee(aliceToken, "25.00");

        checkout(bobToken).andExpect(status().isOk());
        checkout(bobToken).andExpect(status().isOk());

        entityManager.flush();
        verify(intents, times(1)).create(any(PaymentIntentCreateParams.class), any(RequestOptions.class));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM dues", Integer.class)).isEqualTo(1);
    }

    // A fee change applies to dues not started yet; a payment already started keeps its amount.
    @Test
    void aNewFee_doesntChangeDuesAlreadyStarted() throws Exception {
        asblService.linkStripeAccount(club, "acct_club");
        setFee(aliceToken, "25.00");
        checkout(bobToken).andExpect(status().isOk());

        setFee(aliceToken, "30.00").andExpect(status().isNoContent());

        checkout(bobToken).andExpect(jsonPath("$.amount").value(25.00));
        myDues(bobToken).andExpect(jsonPath("$[0].amount").value(25.00));
    }

    @Test
    void onlyAdministratorsSetTheFee_andOnlyOnceTheyCanReceivePayments() throws Exception {
        setFee(bobToken, "25.00").andExpect(status().isForbidden());
        setFee(aliceToken, "25.00").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENTS_DISABLED"));

        asblService.linkStripeAccount(club, "acct_club");
        setFee(aliceToken, "0.50").andExpect(status().isBadRequest());
        setFee(aliceToken, "25.00").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/asbls/mon-club/manage/dues").header("Authorization", "Bearer " + aliceToken))
                .andExpect(jsonPath("$.annualFee").value(25.00))
                .andExpect(jsonPath("$.paymentsEnabled").value(true));

        setFee(aliceToken, null).andExpect(status().isNoContent()); // stop collecting dues
        myDues(bobToken).andExpect(jsonPath("$.length()").value(0));
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'DUES_FEE_CHANGED'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void outsiders_cannotPay_andThereIsNothingToPayWithoutAFee() throws Exception {
        asblService.linkStripeAccount(club, "acct_club");
        checkout(bobToken).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_DUES"));
        myDues(bobToken).andExpect(jsonPath("$.length()").value(0));

        setFee(aliceToken, "25.00");
        checkout(carolToken).andExpect(status().isForbidden());
        myDues(carolToken).andExpect(jsonPath("$.length()").value(0));
    }

    // The treasurer's follow-up: every current member, paid or not; as a spreadsheet too (audited: personal data).
    @Test
    void theTreasurer_seesWhoPaid_andCanDownloadIt() throws Exception {
        User dave = userService.register("Dave", "dave@club.test", "password123");
        membershipService.joinByInvitation(dave, club);
        membershipService.changeRole(club, dave.getPublicId(), MembershipRole.TREASURER);
        String daveToken = tokenFor("dave@club.test");
        asblService.linkStripeAccount(club, "acct_club");
        setFee(aliceToken, "25.00");
        checkout(bobToken);
        mockMvc.perform(StripeWebhooks.signed("evt_dues_2", "payment_intent.succeeded", "pi_dues_1"));

        report(daveToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(duesService.currentYear()))
                .andExpect(jsonPath("$.paid").value(1))
                .andExpect(jsonPath("$.members[*].name").value(org.hamcrest.Matchers.contains("Alice", "Bob", "Dave")))
                .andExpect(jsonPath("$.members[1].paid").value(true))
                .andExpect(jsonPath("$.members[1].amount").value(25.00))
                .andExpect(jsonPath("$.members[1].paidAt").exists())
                .andExpect(jsonPath("$.members[0].paid").value(false));

        byte[] csv = mockMvc.perform(get("/api/v1/asbls/mon-club/manage/dues/members/export")
                        .header("Authorization", "Bearer " + daveToken).header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).startsWith("\uFEFFName;Email;Dues;Amount (EUR);Paid at\r\n")
                .contains("Alice;alice@club.test;Unpaid;;\r\n")
                .containsPattern("Bob;bob@club.test;Paid;25\\.00;\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}\r\n");
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'DUES_EXPORTED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void plainMembersAndOutsiders_cannotSeeWhoPaid() throws Exception {
        report(bobToken).andExpect(status().isForbidden());
        report(carolToken).andExpect(status().isForbidden());
    }

    // CSV injection: a name typed as a formula stays text in the administrator's spreadsheet.
    @Test
    void aNameThatLooksLikeAFormula_staysText_inTheSpreadsheet() throws Exception {
        membershipService.joinByInvitation(
                userService.register("=HYPERLINK(\"https://evil.test\")", "eve@club.test", "password123"), club);

        String text = mockMvc.perform(get("/api/v1/asbls/mon-club/manage/dues/members/export")
                        .header("Authorization", "Bearer " + aliceToken))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(text).contains("\"'=HYPERLINK(\"\"https://evil.test\"\")\"");
    }

    private ResultActions report(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/asbls/mon-club/manage/dues/members").header("Authorization", "Bearer " + token));
    }

    private ResultActions setFee(String token, String fee) throws Exception {
        return mockMvc.perform(put("/api/v1/asbls/mon-club/manage/dues").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(fee == null ? "{\"annualFee\": null}" : "{\"annualFee\": " + fee + "}"));
    }

    private ResultActions myDues(String token) throws Exception {
        return mockMvc.perform(get("/api/v1/me/dues").header("Authorization", "Bearer " + token));
    }

    private ResultActions checkout(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/asbls/mon-club/dues/checkout").header("Authorization", "Bearer " + token));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
