package club.asbl.asbl_club.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.asbl.StripeConnectService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// The API behind the CDN: requests relayed by our proxy function (it knows the secret) are the visitor's own,
// at the public site; anything else is taken exactly as it arrived.
@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "edge.secret=test-edge-secret",
        "edge.public-url=https://asbl-club.pages.dev"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EdgeProxyIntegrationTest {

    private static final String SITE = "https://asbl-club.pages.dev";
    private static final String VISITOR_IP = "203.0.113.42";

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

    User alice;

    @BeforeEach
    void aUser() {
        alice = userService.register("Alice", "alice@club.test", "password123");
    }

    // A login posted from the site is same-origin once relayed: accepted, and audited with the visitor's IP.
    @Test
    void aLoginRelayedByTheProxy_isTheVisitors() throws Exception {
        mockMvc.perform(viaProxy(login(), "test-edge-secret")).andExpect(status().isOk());

        assertThat(lastLoginIp()).isEqualTo(VISITOR_IP);
    }

    // Without the right secret nothing is trusted: a POST from the site's origin arriving at another host is
    // cross-origin (refused), exactly as before this filter existed.
    @Test
    void aWrongSecret_isNotTrusted() throws Exception {
        mockMvc.perform(viaProxy(login(), "guessed")).andExpect(status().isForbidden());
    }

    // Someone calling the server directly can't choose the IP written to the audit log or used for rate limiting.
    @Test
    void aFakedClientIp_withoutTheSecret_isIgnored() throws Exception {
        mockMvc.perform(login().header(EdgeProxyFilter.CLIENT_IP_HEADER, VISITOR_IP)).andExpect(status().isOk());

        assertThat(lastLoginIp()).isEqualTo("127.0.0.1");
    }

    // Links the server builds (here: where Stripe sends the administrator back) point at the public site.
    @Test
    void linksBuiltByTheServer_pointAtThePublicSite() throws Exception {
        asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        when(stripeConnectService.startOnboarding(any(), any(), any())).thenReturn("https://connect.stripe.com/x");
        String token = JsonPath.read(mockMvc.perform(login()).andReturn().getResponse().getContentAsString(),
                "$.accessToken");

        mockMvc.perform(viaProxy(post("/api/v1/asbls/mon-club/manage/payments/onboarding")
                        .header("Authorization", "Bearer " + token), "test-edge-secret"))
                .andExpect(status().isOk());

        verify(stripeConnectService).startOnboarding(any(), eq(SITE + "/asbls/mon-club/manage/payments?resume"),
                eq(SITE + "/asbls/mon-club/manage/payments"));
    }

    // What the proxy function sends: the browser's request (with its Origin), plus the secret and the visitor's IP.
    // It reaches the server at the host's own address, not the site's.
    private static MockHttpServletRequestBuilder viaProxy(MockHttpServletRequestBuilder request, String secret) {
        return request
                .with(r -> {
                    r.setScheme("https");
                    r.setServerName("asbl-club.onrender.com");
                    r.setServerPort(443);
                    return r;
                })
                .header("Origin", SITE)
                .header(EdgeProxyFilter.SECRET_HEADER, secret)
                .header(EdgeProxyFilter.CLIENT_IP_HEADER, VISITOR_IP);
    }

    private static MockHttpServletRequestBuilder login() {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"alice@club.test\", \"password\": \"password123\"}");
    }

    private String lastLoginIp() {
        return jdbcTemplate.queryForObject("SELECT ip FROM audit_logs WHERE action = 'LOGIN_SUCCEEDED' AND user_id = ? "
                + "ORDER BY id DESC LIMIT 1", String.class, alice.getId());
    }
}
