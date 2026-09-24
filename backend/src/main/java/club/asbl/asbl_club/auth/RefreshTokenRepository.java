package club.asbl.asbl_club.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // SELECT ... FOR UPDATE: a second refresh with the same token waits until the first one has
    // committed, then sees it revoked. Without the lock both could pass the check and each get a new token.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
