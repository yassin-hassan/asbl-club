package club.asbl.asbl_club.dues;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.user.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DueRepository extends JpaRepository<Due, Long> {

    Optional<Due> findByAsblAndUserAndYear(Asbl asbl, User user, int year);
}
