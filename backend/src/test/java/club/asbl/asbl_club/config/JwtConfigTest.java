package club.asbl.asbl_club.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    // --- tokens (with a temporary key, as in dev and tests) ---

    @Test
    void decodesATokenItSigned() throws Exception {
        RSAKey key = config.jwtSigningKey("");
        String token = encode(config.jwtEncoder(key), JwtConfig.ISSUER, inFifteenMinutes());

        Jwt jwt = config.jwtDecoder(key).decode(token);

        assertThat(jwt.getSubject()).isEqualTo("alice@example.com");
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", key.getKeyID());
    }

    @Test
    void rejectsATokenWhosePayloadWasChanged() throws Exception {
        RSAKey key = config.jwtSigningKey("");
        String[] parts = encode(config.jwtEncoder(key), JwtConfig.ISSUER, inFifteenMinutes()).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("alice@example.com", "mallory@example.com");
        String forgedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThatThrownBy(() -> config.jwtDecoder(key).decode(forgedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        RSAKey key = config.jwtSigningKey("");
        String token = encode(config.jwtEncoder(key), JwtConfig.ISSUER, Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> config.jwtDecoder(key).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() throws Exception {
        JwtDecoder decoder = config.jwtDecoder(config.jwtSigningKey(""));
        String token = encode(config.jwtEncoder(config.jwtSigningKey("")), JwtConfig.ISSUER, inFifteenMinutes());

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenFromAnotherIssuer() throws Exception {
        RSAKey key = config.jwtSigningKey("");
        String token = encode(config.jwtEncoder(key), "someone-else", inFifteenMinutes());

        assertThatThrownBy(() -> config.jwtDecoder(key).decode(token)).isInstanceOf(JwtException.class);
    }

    // --- configured key (production) ---

    @Test
    void aConfiguredKey_survivesARestart() throws Exception {
        String pem = newPkcs8Pem();
        // Two independent "application starts" (or two instances) given the same secret.
        RSAKey beforeRestart = config.jwtSigningKey(pem);
        RSAKey afterRestart = config.jwtSigningKey(pem);
        String token = encode(config.jwtEncoder(beforeRestart), JwtConfig.ISSUER, inFifteenMinutes());

        assertThat(config.jwtDecoder(afterRestart).decode(token).getSubject()).isEqualTo("alice@example.com");
        assertThat(afterRestart.getKeyID()).isEqualTo(beforeRestart.getKeyID());
    }

    @Test
    void anInvalidConfiguredKey_stopsTheApplicationWithAClearMessage() {
        assertThatThrownBy(() -> config.jwtSigningKey("not a key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SIGNING_KEY")
                .hasMessageNotContaining("not a key");
    }

    private static Instant inFifteenMinutes() {
        return Instant.now().plus(15, ChronoUnit.MINUTES);
    }

    private static String encode(JwtEncoder encoder, String issuer, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("alice@example.com")
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES))
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // What `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048` produces.
    private static String newPkcs8Pem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----\n";
    }
}
