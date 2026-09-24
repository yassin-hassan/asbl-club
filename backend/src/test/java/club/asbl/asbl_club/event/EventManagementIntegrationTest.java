package club.asbl.asbl_club.event;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
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

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class EventManagementIntegrationTest {

    private static final String BASE = "/api/v1/asbls/mon-club/manage/events";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Autowired
    AsblService asblService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EventService eventService;

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
    void anAdministrator_createsADraft_addsTickets_andPublishesIt() throws Exception {
        String body = createEvent(adminToken)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith(BASE + "/")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.startsAt").value("2026-12-01T19:00:00Z"))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(body, "$.id");

        // A draft isn't public yet.
        mockMvc.perform(get("/api/v1/events/" + id)).andExpect(status().isNotFound());

        send(post(BASE + "/" + id + "/tickets"), adminToken,
                "{\"label\": \"Standard\", \"price\": 12.50, \"totalSeats\": 100}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tickets[0].label").value("Standard"))
                .andExpect(jsonPath("$.tickets[0].soldSeats").value(0));

        send(post(BASE + "/" + id + "/publish"), adminToken, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/events/" + id)).andExpect(status().isOk());
    }

    @Test
    void aMember_readsEverythingIncludingDrafts_butCannotChangeAnything() throws Exception {
        Integer id = JsonPath.read(createEvent(adminToken).andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canManage").value(false))
                .andExpect(jsonPath("$.events[0].status").value("DRAFT"));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk());

        createEvent(memberToken).andExpect(status().isForbidden());
        send(post(BASE + "/" + id + "/publish"), memberToken, "").andExpect(status().isForbidden());
    }

    @Test
    void anOutsider_isRefusedEverywhere_andAnonymousNeedsToLogIn() throws Exception {
        mockMvc.perform(get(BASE).header("Authorization", "Bearer " + outsiderToken)).andExpect(status().isForbidden());
        createEvent(outsiderToken).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    // IDOR: an admin of one association must not reach another association's event by putting its ID under
    // their own association's URL.
    @Test
    void anEventOfAnotherAssociation_isNotFoundUnderThisOne() throws Exception {
        User other = userService.register("Other", "other@club.test", "password123");
        Asbl otherClub = asblService.createAsbl(other, "Other Club", "0987.654.321", "other-club", "fr");
        Event foreign = eventServiceCreate(otherClub);

        mockMvc.perform(get(BASE + "/" + foreign.getId()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        send(post(BASE + "/" + foreign.getId() + "/publish"), adminToken, "").andExpect(status().isNotFound());
    }

    @Test
    void invalidEvents_areRejectedPerField() throws Exception {
        send(post(BASE), adminToken, "{\"title\": \"\", \"visibility\": \"EVERYONE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.startsAt").exists())
                .andExpect(jsonPath("$.errors.visibility").exists());
    }

    private Event eventServiceCreate(Asbl asbl) {
        return eventService.createEvent(asbl, "Foreign", null, Instant.parse("2026-12-01T19:00:00Z"),
                null, "PUBLIC");
    }

    private ResultActions createEvent(String token) throws Exception {
        return send(post(BASE), token, """
                {"title": "Concert", "description": "A concert", "startsAt": "2026-12-01T19:00:00Z",
                 "location": "Hall", "visibility": "PUBLIC"}
                """);
    }

    private ResultActions send(MockHttpServletRequestBuilder request,
            String token, String json) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String tokenFor(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"password123\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
