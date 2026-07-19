package club.asbl.asbl_club;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistrationLoginIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void loginPageIsPubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    void registeringLogsTheUserInAutomatically() throws Exception {
        MvcResult result = mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Alice")
                        .param("email", "alice@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void registeredUserCanLogInManually() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Carol")
                        .param("email", "carol@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(formLogin().user("carol@example.com").password("password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loggingInWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                        .param("name", "Bob")
                        .param("email", "bob@example.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(formLogin().user("bob@example.com").password("wrongpassword"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }
}
