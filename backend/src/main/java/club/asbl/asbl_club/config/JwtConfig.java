package club.asbl.asbl_club.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    // Written into every token as "iss" and required by the decoder.
    public static final String ISSUER = "asbl-club";

    // Claim holding the user's roles, without Spring's "ROLE_" prefix: ["USER", "SUPERADMIN"].
    public static final String ROLES_CLAIM = "roles";

    /**
     * The key tokens are signed with. In production it comes from configuration (a PKCS#8 PEM private key in
     * the JWT_SIGNING_KEY secret), so it's the same across restarts and instances. Without it (local dev,
     * tests) a temporary key is generated: tokens then die with the process, which is fine there.
     *
     * <p>The key ID ("kid") is the key's thumbprint (RFC 7638). Every token names the key that signed it,
     * which is what makes rotating to a new key possible later.
     */
    @Bean
    RSAKey jwtSigningKey(@Value("${jwt.signing-key:}") String privateKeyPem) {
        KeyPair keyPair;
        if (privateKeyPem.isBlank()) {
            log.warn("No JWT signing key configured (JWT_SIGNING_KEY): using a temporary key. "
                    + "Access tokens won't survive a restart. Configure a key in production.");
            keyPair = generateKeyPair();
        } else {
            keyPair = parsePkcs8Pem(privateKeyPem);
        }
        try {
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey(keyPair.getPrivate())
                    .keyIDFromThumbprint()
                    .build();
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not compute the JWT signing key ID", e);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        // Signs with this key and writes its "kid" into every token header.
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
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

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is not available in this JVM", e);
        }
    }

    // Reads "-----BEGIN PRIVATE KEY-----" (PKCS#8, what `openssl genpkey` writes) and derives the public key
    // from it, so only one secret has to be configured.
    private static KeyPair parsePkcs8Pem(String pem) {
        try {
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) rsa.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) rsa.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new KeyPair(publicKey, privateKey);
        } catch (GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            // Deliberately without the key material in the message.
            throw new IllegalStateException(
                    "JWT_SIGNING_KEY is not a valid PKCS#8 PEM RSA private key (-----BEGIN PRIVATE KEY-----)", e);
        }
    }
}
