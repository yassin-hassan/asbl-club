package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.config.JwtConfig;
import club.asbl.asbl_club.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class TokenService {

    static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtEncoder jwtEncoder;

    TokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    TokenResponse issueAccessToken(User user, Collection<? extends GrantedAuthority> authorities) {
        Instant now = Instant.now();
        // Only roles go into the token ("ROLE_SUPERADMIN" -> "SUPERADMIN"); other authorities are skipped.
        List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                // Stable identity: emails change and get reused, the public ID never does.
                .subject(user.getPublicId().toString())
                // Display data only, never used to identify the user.
                .claim("email", user.getEmail())
                .claim(JwtConfig.ROLES_CLAIM, roles)
                .issuedAt(now)
                .expiresAt(now.plus(ACCESS_TOKEN_TTL))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ACCESS_TOKEN_TTL.toSeconds());
    }
}
