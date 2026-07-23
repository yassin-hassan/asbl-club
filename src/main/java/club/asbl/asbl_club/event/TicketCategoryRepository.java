package club.asbl.asbl_club.event;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findByEvent(Event event);
}
