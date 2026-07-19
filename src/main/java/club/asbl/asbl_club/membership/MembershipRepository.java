package club.asbl.asbl_club.membership;

import club.asbl.asbl_club.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipRepository extends JpaRepository<Membership, Long> {

    @EntityGraph(attributePaths = "asbl")
    List<Membership> findByUser(User user);
}
