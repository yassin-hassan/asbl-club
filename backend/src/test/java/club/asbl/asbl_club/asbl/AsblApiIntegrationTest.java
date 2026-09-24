package club.asbl.asbl_club.asbl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class AsblApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    User alice;
    String aliceToken;

    @BeforeEach
    void aliceLogsIn() throws Exception {
        alice = userService.register("Alice", "alice@club.test", "password123");
        aliceToken = tokenFor("alice@club.test");
    }

    @Test
    void create_makesMeItsAdministrator() throws Exception {
        create("Mon Club", "0123.456.789", "mon-club")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/asbls/mon-club"))
                .andExpect(jsonPath("$.slug").value("mon-club"));

        mockMvc.perform(get("/api/v1/asbls/mon-club/members").header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myRole").value("ADMIN"))
                .andExpect(jsonPath("$.paymentsEnabled").value(false))
                .andExpect(jsonPath("$.members[0].email").value("alice@club.test"));
    }

    @Test
    void create_takenSlugOrBceNumber_isAConflictOnThatField() throws Exception {
        asblService.createAsbl(alice, "Existing", "0999.999.999", "taken", "fr");

        create("Other", "0123.456.789", "taken").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.slug").exists());
        create("Other", "0999.999.999", "free-slug").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.bceNumber").exists());
    }

    @Test
    void create_invalidValues_areRejectedPerField() throws Exception {
        create("Mon Club", "123", "Not A Slug!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bceNumber").exists())
                .andExpect(jsonPath("$.errors.slug").exists());
    }

    @Test
    void members_areForMembersOnly() throws Exception {
        asblService.createAsbl(alice, "Mon Club", "0123.456.789", "mon-club", "fr");
        userService.register("Bob", "bob@club.test", "password123");
        String bobToken = tokenFor("bob@club.test");

        mockMvc.perform(get("/api/v1/asbls/mon-club/members").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isForbidden());
        // Anonymous: refused by the URL rules themselves, before any controller code runs.
        mockMvc.perform(get("/api/v1/asbls/mon-club/members")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/asbls/nope/members").header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        // The association itself stays public.
        mockMvc.perform(get("/api/v1/asbls/mon-club")).andExpect(status().isOk());
    }

    private ResultActions create(String denomination, String bceNumber, String slug) throws Exception {
        return mockMvc.perform(post("/api/v1/asbls")
                .header("Authorization", "Bearer " + aliceToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"denomination": "%s", "bceNumber": "%s", "slug": "%s", "defaultLanguage": "fr"}
                        """.formatted(denomination, bceNumber, slug)));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
