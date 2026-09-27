package club.asbl.asbl_club.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Query("select t from EmailVerificationToken t join fetch t.user where t.tokenHash = :hash")
    Optional<EmailVerificationToken> findByTokenHash(@Param("hash") String hash);

    // A new link replaces the ones sent before: only the latest email works.
    @Modifying
    @Query(value = "DELETE FROM email_verification_tokens WHERE user_id = :userId AND used_at IS NULL", nativeQuery = true)
    int deleteUnusedOf(@Param("userId") Long userId);

    // Uses the link, once: only if unused and not expired, checked by the database in the same statement, so a
    // link clicked twice at the same moment logs in once.
    @Modifying
    @Query(value = "UPDATE email_verification_tokens SET used_at = :now "
            + "WHERE id = :id AND used_at IS NULL AND expires_at > :now", nativeQuery = true)
    int use(@Param("id") Long id, @Param("now") Instant now);
}
