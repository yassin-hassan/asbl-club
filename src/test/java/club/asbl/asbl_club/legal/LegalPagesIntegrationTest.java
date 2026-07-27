package club.asbl.asbl_club.legal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LegalPagesIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void legalPages_areReachableWithoutLogin() throws Exception {
        mockMvc.perform(get("/legal")).andExpect(status().isOk());
        mockMvc.perform(get("/privacy")).andExpect(status().isOk());
        mockMvc.perform(get("/cookies")).andExpect(status().isOk());
    }
}
