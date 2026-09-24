package club.asbl.asbl_club.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

@Configuration
public class JwtConfig {

    // Written into every token as "iss" and required by the decoder.
    public static final String ISSUER = "asbl-club";

    // Claim holding the user's roles, without Spring's "ROLE_" prefix: ["USER", "SUPERADMIN"].
    public static final String ROLES_CLAIM = "roles";

    // Temporary: a new key pair on every start, so tokens don't survive a restart and aren't
    // shared between instances. Replaced by a configured key later (roadmap Phase 2, slice 8).
    @Bean
    KeyPair jwtSigningKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is not available in this JVM", e);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair jwtSigningKeyPair) {
        return NimbusJwtEncoder
                .withKeyPair((RSAPublicKey) jwtSigningKeyPair.getPublic(),
                        (RSAPrivateKey) jwtSigningKeyPair.getPrivate())
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair jwtSigningKeyPair) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) jwtSigningKeyPair.getPublic())
                .build();
        // Default checks (exp / nbf) plus: "iss" must be ours.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    // Turns a verified token into Spring authorities: "roles": ["SUPERADMIN"] -> ROLE_SUPERADMIN,
    // so hasRole("SUPERADMIN") works on /api. By default Spring would read the "scope" claim instead.
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(ROLES_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
