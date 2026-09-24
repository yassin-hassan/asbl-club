package club.asbl.asbl_club.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import club.asbl.asbl_club.user.UserService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// /api/v1/admin/** has no endpoints yet, so the rule is observed through the status code:
// 403 = stopped by security, 404 = let through by security (and then nothing is there).
@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class ApiRoleAuthorizationIntegrationTest {

    private static final String ADMIN_URL = "/api/v1/admin/anything";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserService userService;

    @Test
    void adminArea_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get(ADMIN_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void adminArea_withRegularUserToken_isForbidden() throws Exception {
        userService.register("Alice", "alice@club.test", "password123");
        String token = accessTokenFor("alice@club.test", "password123");

        mockMvc.perform(get(ADMIN_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminArea_withSuperAdminToken_isLetThrough() throws Exception {
        userService.registerSuperAdmin("Root", "root@club.test", "password123");
        String token = accessTokenFor("root@club.test", "password123");

        mockMvc.perform(get(ADMIN_URL).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String accessTokenFor(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
