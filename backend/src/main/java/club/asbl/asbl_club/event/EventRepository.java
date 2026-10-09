package club.asbl.asbl_club.event;

import club.asbl.asbl_club.asbl.Asbl;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByAsbl(Asbl asbl);

    Optional<Event> findByIdAndAsbl(Long id, Asbl asbl);

    @Query("select e from Event e join fetch e.asbl where e.id = :id")
    Optional<Event> findByIdFetchingAsbl(Long id);

    List<Event> findByVisibilityAndStatusOrderByStartsAtDesc(EventVisibility visibility, EventStatus status);

    List<Event> findByAsblAndVisibilityAndStatusOrderByStartsAtDesc(
            Asbl asbl, EventVisibility visibility, EventStatus status);

    // The platform's catalogue: public, published events still to come, soonest first, with their association.
    @Query("select e from Event e join fetch e.asbl where e.visibility = club.asbl.asbl_club.event.EventVisibility.PUBLIC "
            + "and e.status = club.asbl.asbl_club.event.EventStatus.PUBLISHED and e.startsAt >= :from "
            + "order by e.startsAt asc")
    List<Event> findUpcomingPublic(@Param("from") Instant from);
}
