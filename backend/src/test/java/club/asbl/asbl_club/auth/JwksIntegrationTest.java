package club.asbl.asbl_club.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import club.asbl.asbl_club.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwksIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RSAKey jwtSigningKey;

    @Test
    void publishesThePublicSigningKeyWithoutLoggingIn() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys.length()").value(1))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").value(jwtSigningKey.getKeyID()))
                .andExpect(jsonPath("$.keys[0].n").value(jwtSigningKey.getModulus().toString()));
    }

    @Test
    void neverPublishesPrivateKeyMaterial() throws Exception {
        String body = mockMvc.perform(get("/.well-known/jwks.json")).andReturn().getResponse().getContentAsString();
        Map<String, Object> key = JsonPath.read(body, "$.keys[0]");

        // d, p, q, dp, dq, qi are the private parts of an RSA JWK.
        assertThat(key).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }
}
