package club.asbl.asbl_club.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RegistrationRepository extends JpaRepository<Registration, Long> {

    @Query("select r from Registration r join fetch r.event e join fetch e.asbl join fetch r.ticketCategory where r.id = :id")
    Optional<Registration> findByIdWithEventAndAsbl(@Param("id") Long id);

    // Flush first: clearing afterwards would otherwise discard changes not yet written (the event's new status).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Registration r set r.status = club.asbl.asbl_club.payment.RegistrationStatus.CANCELLED "
            + "where r.event.id = :eventId and r.status = club.asbl.asbl_club.payment.RegistrationStatus.RESERVED")
    int cancelUnpaid(@Param("eventId") Long eventId);
}
