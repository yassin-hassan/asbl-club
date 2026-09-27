package club.asbl.asbl_club.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    @Query("select t from PasswordResetToken t join fetch t.user where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByTokenHash(@Param("hash") String hash);

    // A new request replaces the links sent before: only the latest email works.
    @Modifying
    @Query(value = "DELETE FROM password_reset_tokens WHERE user_id = :userId AND used_at IS NULL", nativeQuery = true)
    int deleteUnusedOf(@Param("userId") Long userId);

    // Uses the link, once: only if unused and not expired, checked by the database in the same statement, so two
    // clicks at the same moment can't both change the password.
    @Modifying
    @Query(value = "UPDATE password_reset_tokens SET used_at = :now "
            + "WHERE id = :id AND used_at IS NULL AND expires_at > :now", nativeQuery = true)
    int use(@Param("id") Long id, @Param("now") Instant now);
}
