package club.asbl.asbl_club.email;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OutgoingEmailRepository extends JpaRepository<OutgoingEmail, Long> {

    // The next email due, locked for this transaction. SKIP LOCKED: an email another sender (another instance) is
    // busy with is skipped rather than waited for, so two instances never send the same email at once.
    @Query(value = "SELECT * FROM email_outbox WHERE status = 'PENDING' AND next_attempt_at <= :now "
            + "ORDER BY next_attempt_at LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<OutgoingEmail> claimNextDue(@Param("now") Instant now);
}
