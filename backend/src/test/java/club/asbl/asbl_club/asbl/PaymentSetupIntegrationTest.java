package club.asbl.asbl_club.asbl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.StripeConnectService.ConnectStatus;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import com.stripe.exception.ApiConnectionException;
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
import org.springframework.transaction.annotation.Transactional;

// Stripe itself is replaced by a stand-in (an external service with real keys); everything else is real.
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PaymentSetupIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    StripeConnectService stripeConnectService;

    String adminToken;
    String memberToken;
    String outsiderToken;

    @BeforeEach
    void anAssociationWithAnAdminAMemberAndAnOutsider() throws Exception {
        User admin = userService.register("Admin", "admin@club.test", "password123");
        Asbl club = asblService.createAsbl(admin, "Mon Club", "0123.456.789", "mon-club", "fr");
        User member = userService.register("Member", "member@club.test", "password123");
        jdbcTemplate.update("INSERT INTO memberships (user_id, asbl_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')",
                member.getId(), club.getId());
        userService.register("Outsider", "outsider@club.test", "password123");
        adminToken = tokenFor("admin@club.test");
        memberToken = tokenFor("member@club.test");
        outsiderToken = tokenFor("outsider@club.test");
    }

    @Test
    void anAdmin_seesWhetherTheAssociationCanReceivePayments() throws Exception {
        when(stripeConnectService.status(any())).thenReturn(ConnectStatus.PENDING);

        mockMvc.perform(get("/api/v1/asbls/mon-club/manage/payments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denomination").value("Mon Club"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // Stripe sends the administrator back to the Angular page, on the address they came from.
    @Test
    void anAdmin_getsAnOnboardingLink_thatReturnsToThePaymentsPage() throws Exception {
        when(stripeConnectService.startOnboarding(any(), any(), any())).thenReturn("https://connect.stripe.com/setup/x");

        mockMvc.perform(post("/api/v1/asbls/mon-club/manage/payments/onboarding")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://connect.stripe.com/setup/x"));

        verify(stripeConnectService).startOnboarding(any(),
                eq("http://localhost/asbls/mon-club/manage/payments?resume"),
                eq("http://localhost/asbls/mon-club/manage/payments"));
    }

    @Test
    void membersAndOutsiders_cannotSeeOrChangeThePaymentSetup() throws Exception {
        for (String token : new String[] {memberToken, outsiderToken}) {
            mockMvc.perform(get("/api/v1/asbls/mon-club/manage/payments").header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/asbls/mon-club/manage/payments/onboarding")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(post("/api/v1/asbls/mon-club/manage/payments/onboarding"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(stripeConnectService);
    }

    @Test
    void stripeBeingUnreachable_isABadGateway() throws Exception {
        when(stripeConnectService.status(any())).thenThrow(new ApiConnectionException("timeout"));

        mockMvc.perform(get("/api/v1/asbls/mon-club/manage/payments").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("The payment provider couldn't be reached."));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
