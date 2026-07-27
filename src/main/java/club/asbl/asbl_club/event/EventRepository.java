package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByAsbl(Asbl asbl);

    Optional<Event> findByIdAndAsbl(Long id, Asbl asbl);

    List<Event> findByVisibilityAndStatusOrderByStartsAtDesc(String visibility, String status);

    List<Event> findByAsblAndVisibilityAndStatusOrderByStartsAtDesc(Asbl asbl, String visibility, String status);
}
