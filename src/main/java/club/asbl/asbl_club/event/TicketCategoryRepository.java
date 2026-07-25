package club.asbl.asbl_club.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findByEvent(Event event);

    @Modifying
    @Query("update TicketCategory t set t.soldSeats = t.soldSeats + 1 "
            + "where t.id = :id and t.soldSeats < t.totalSeats")
    int reserveOneSeat(@Param("id") Long id);
}
