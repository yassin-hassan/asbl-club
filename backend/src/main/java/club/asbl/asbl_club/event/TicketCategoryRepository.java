package club.asbl.asbl_club.event;

import java.math.BigDecimal;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TicketCategoryRepository extends JpaRepository<TicketCategory, Long> {

    List<TicketCategory> findByEvent(Event event);

    // One statement: the new seat count applies only if it isn't below the seats already taken.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TicketCategory t set t.label = :label, t.price = :price, t.totalSeats = :totalSeats "
            + "where t.id = :id and t.soldSeats <= :totalSeats")
    int update(@Param("id") Long id, @Param("label") String label, @Param("price") BigDecimal price,
            @Param("totalSeats") int totalSeats);

    // Any booking, whatever its status (bookings reference their category for good).
    @Query(value = "select exists(select 1 from registrations where ticket_category_id = :id)", nativeQuery = true)
    boolean hasBookings(@Param("id") Long id);

    @Modifying
    @Query("update TicketCategory t set t.soldSeats = t.soldSeats + 1 "
            + "where t.id = :id and t.soldSeats < t.totalSeats")
    int reserveOneSeat(@Param("id") Long id);
}
