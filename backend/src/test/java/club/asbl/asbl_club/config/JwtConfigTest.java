package club.asbl.asbl_club.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
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
    private final KeyPair keyPair = config.jwtSigningKeyPair();
    private final JwtEncoder encoder = config.jwtEncoder(keyPair);
    private final JwtDecoder decoder = config.jwtDecoder(keyPair);

    @Test
    void decodesATokenItSigned() {
        String token = encode("alice@example.com", Instant.now().plus(15, ChronoUnit.MINUTES));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("alice@example.com");
        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256");
    }

    @Test
    void rejectsATokenWhosePayloadWasChanged() {
        String token = encode("alice@example.com", Instant.now().plus(15, ChronoUnit.MINUTES));
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("alice@example.com", "mallory@example.com");
        String forgedToken = parts[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(forgedPayload.getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(forgedToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = encode("alice@example.com", Instant.now().minus(5, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        JwtEncoder otherEncoder = config.jwtEncoder(config.jwtSigningKeyPair());
        String token = encode(otherEncoder, "alice@example.com", Instant.now().plus(15, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String encode(String subject, Instant expiresAt) {
        return encode(encoder, subject, expiresAt);
    }

    private static String encode(JwtEncoder encoder, String subject, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES))
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
