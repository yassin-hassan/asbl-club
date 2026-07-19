package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MembershipRepository extends JpaRepository<Membership, Long> {

    List<Membership> findByUser(User user);
}
