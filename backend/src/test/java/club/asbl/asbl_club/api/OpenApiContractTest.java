package club.asbl.asbl_club.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

// The OpenAPI document is the contract the Angular client is generated from. This test fails when the
// API changes but the committed contract doesn't, so the frontend can't silently drift from the backend.
// After an intended API change: ./mvnw test -Dtest=OpenApiContractTest -Dopenapi.update=true
// then regenerate the client: npm run api:generate (in frontend/).
@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "springdoc.api-docs.enabled=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTest {

    private static final Path CONTRACT = Path.of("../frontend/openapi/asbl-club-api.json");

    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @Test
    void committedContractMatchesTheApi() throws Exception {
        String document = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode actual = json.readTree(document);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(CONTRACT.getParent());
            Files.writeString(CONTRACT, json.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        }

        assertThat(CONTRACT).as("Contract missing: run with -Dopenapi.update=true").exists();
        assertThat(json.readTree(Files.readString(CONTRACT)))
                .as("The API changed: run with -Dopenapi.update=true, then npm run api:generate")
                .isEqualTo(actual);
    }
}
