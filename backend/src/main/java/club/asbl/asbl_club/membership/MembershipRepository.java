package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipRepository extends JpaRepository<Membership, Long> {

    @EntityGraph(attributePaths = "asbl")
    List<Membership> findByUser(User user);

    @EntityGraph(attributePaths = "user")
    List<Membership> findByAsbl(Asbl asbl);

    Optional<Membership> findByUserAndAsbl(User user, Asbl asbl);

    boolean existsByUserAndAsblAndRoleAndStatus(User user, Asbl asbl, MembershipRole role, MembershipStatus status);

    Optional<Membership> findByAsblAndUser_PublicId(Asbl asbl, UUID userPublicId);
}
