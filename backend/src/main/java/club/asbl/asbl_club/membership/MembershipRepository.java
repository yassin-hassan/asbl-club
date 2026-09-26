package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface MembershipRepository extends JpaRepository<Membership, Long> {

    @EntityGraph(attributePaths = "asbl")
    List<Membership> findByUser(User user);

    @EntityGraph(attributePaths = "user")
    List<Membership> findByAsbl(Asbl asbl);

    Optional<Membership> findByUserAndAsbl(User user, Asbl asbl);

    boolean existsByUserAndAsblAndRoleAndStatus(User user, Asbl asbl, MembershipRole role, MembershipStatus status);

    Optional<Membership> findByAsblAndUser_PublicId(Asbl asbl, UUID userPublicId);

    // The association's active administrators, locked until the transaction ends (SELECT … FOR UPDATE): two
    // administrators demoting each other at the same moment can't both see "someone else is still admin".
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Membership> findByAsblAndRoleAndStatus(Asbl asbl, MembershipRole role, MembershipStatus status);
}
