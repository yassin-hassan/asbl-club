package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.config.JwtConfig;
import club.asbl.asbl_club.user.User;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class TokenService {

    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final JwtEncoder jwtEncoder;

    TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    TokenResponse issueAccessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                // Stable identity: emails change and get reused, the public ID never does.
                .subject(user.getPublicId().toString())
                // Display data only, never used to identify the user.
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ACCESS_TOKEN_TTL.toSeconds());
    }
}
