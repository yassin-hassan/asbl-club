package club.asbl.asbl_club.payment;

import java.time.Instant;
import java.util.List;
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

    // Everyone who booked this event, with what the attendee list shows, in one query.
    @Query("select r from Registration r left join fetch r.user join fetch r.ticketCategory "
            + "where r.event.id = :eventId order by r.registeredAt")
    List<Registration> findAttendees(@Param("eventId") Long eventId);

    @Query("select r.id from Registration r where r.status = club.asbl.asbl_club.payment.RegistrationStatus.RESERVED "
            + "and r.registeredAt < :before")
    List<Long> findReservedBefore(@Param("before") Instant before);

    // Only if the booking is still waiting for payment, checked by the database in the same statement: if Stripe's
    // webhook marked it paid a moment ago, this changes nothing (see PaymentService for the other side).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Registration r set r.status = club.asbl.asbl_club.payment.RegistrationStatus.EXPIRED "
            + "where r.id = :id and r.status = club.asbl.asbl_club.payment.RegistrationStatus.RESERVED")
    int expireIfReserved(@Param("id") Long id);
}
