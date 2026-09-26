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

    // The conditional updates below are plain SQL on purpose. Registration is a joined-inheritance entity (payables +
    // registrations), and for those Hibernate turns a JPQL bulk update into "select the ids (a snapshot), then update
    // by id": the condition isn't re-checked when the row is finally locked, and the count is the snapshot's. Two
    // transactions could then both "win". A single-table UPDATE lets Postgres re-check the condition on the locked
    // row and return what it really changed.

    // Flush first: clearing afterwards would otherwise discard changes not yet written (the event's new status).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE registrations SET status = 'CANCELLED' WHERE event_id = :eventId AND status = 'RESERVED'",
            nativeQuery = true)
    int cancelUnpaid(@Param("eventId") Long eventId);

    // My bookings, soonest event first, with what the page shows, in one query.
    @Query("select r from Registration r join fetch r.event e join fetch e.asbl join fetch r.ticketCategory "
            + "where r.user.id = :userId order by e.startsAt")
    List<Registration> findMine(@Param("userId") Long userId);

    // A ticket of this event, by the code in its QR code (a code from another event is simply not found).
    @Query("select r from Registration r left join fetch r.user join fetch r.ticketCategory "
            + "where r.event.id = :eventId and r.qrToken = :code")
    Optional<Registration> findTicket(@Param("eventId") Long eventId, @Param("code") String code);

    // Checks a paid ticket in, once: the database only changes it if it isn't used yet, so two people at the door
    // scanning the same ticket (or a copy of it) at the same moment can't both let someone in.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE registrations SET status = 'ATTENDED', checkin_at = :now "
            + "WHERE event_id = :eventId AND qr_token = :code AND status IN ('PAID', 'CONFIRMED')", nativeQuery = true)
    int checkIn(@Param("eventId") Long eventId, @Param("code") String code, @Param("now") Instant now);

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
    @Query(value = "UPDATE registrations SET status = 'EXPIRED' WHERE id = :id AND status = 'RESERVED'",
            nativeQuery = true)
    int expireIfReserved(@Param("id") Long id);
}
