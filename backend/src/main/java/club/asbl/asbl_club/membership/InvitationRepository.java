package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.asbl.Asbl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface InvitationRepository extends JpaRepository<Invitation, Long> {

    @Query("select i from Invitation i join fetch i.asbl join fetch i.invitedBy where i.tokenHash = :hash")
    Optional<Invitation> findByTokenHash(@Param("hash") String hash);

    @Query("select i from Invitation i where i.asbl = :asbl and i.acceptedAt is null and i.expiresAt > :now "
            + "order by i.createdAt desc")
    List<Invitation> findPending(@Param("asbl") Asbl asbl, @Param("now") Instant now);

    Optional<Invitation> findByIdAndAsbl(Long id, Asbl asbl);

    long countByAsblAndCreatedAtAfter(Asbl asbl, Instant after);

    // Inviting the same address again replaces the earlier, unused invitation: only the newest link works.
    @Modifying
    @Query(value = "DELETE FROM invitations WHERE asbl_id = :asblId AND email = :email AND accepted_at IS NULL",
            nativeQuery = true)
    int deletePending(@Param("asblId") Long asblId, @Param("email") String email);

    // Used once: only if unused and not expired, checked by the database in the same statement.
    @Modifying
    @Query(value = "UPDATE invitations SET accepted_at = :now WHERE id = :id AND accepted_at IS NULL "
            + "AND expires_at > :now", nativeQuery = true)
    int accept(@Param("id") Long id, @Param("now") Instant now);
}
