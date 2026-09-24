package club.asbl.asbl_club.auth;

import club.asbl.asbl_club.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RefreshTokenService {

    static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsService userDetailsService;
    private final SecureRandom secureRandom = new SecureRandom();

    RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserDetailsService userDetailsService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userDetailsService = userDetailsService;
    }

    // What a successful refresh hands back: who the user is, their current roles, and the new raw token.
    record Rotation(User user, Collection<? extends GrantedAuthority> authorities, String refreshToken) {
    }

    // Called at login: starts a new family. Returns the raw token, which is never stored.
    @Transactional
    String issue(User user) {
        return create(user, UUID.randomUUID());
    }

    // Exchanges a valid refresh token for a new one in the same family. The old one can't be used again.
    @Transactional
    Rotation rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isUsable(now))
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = current.getUser();
        // Same lookup as a password login: rejects closed accounts and gives the roles as they are now.
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        } catch (UsernameNotFoundException e) {
            throw new InvalidRefreshTokenException();
        }
        current.revoke(now);
        String newRawToken = create(user, current.getFamilyId());
        return new Rotation(user, userDetails.getAuthorities(), newRawToken);
    }

    private String create(User user, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(
                user, hash(rawToken), familyId, Instant.now().plus(REFRESH_TOKEN_TTL)));
        return rawToken;
    }

    // SHA-256, not Argon2: the token is 256 random bits, so there is nothing to brute-force, and an
    // unsalted hash lets us find the row by hash.
    static String hash(String rawToken) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }
}
