package club.asbl.asbl_club.payment;

import java.time.Instant;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface ProcessedWebhookEventRepository extends Repository<ProcessedWebhookEvent, String> {

    // 1 when the event is new, 0 when it was already recorded. "ON CONFLICT DO NOTHING" rather than catching a
    // unique violation: in Postgres a failed statement aborts the whole transaction. If another delivery of the same
    // event is being handled at this moment, Postgres makes this insert wait for it: 0 if it commits, 1 if it rolled
    // back (then this delivery does the work).
    @Modifying
    @Query(value = "INSERT INTO processed_webhook_events (event_id, type) VALUES (:eventId, :type) "
            + "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int recordIfNew(@Param("eventId") String eventId, @Param("type") String type);

    @Modifying
    @Query(value = "DELETE FROM processed_webhook_events WHERE received_at < :before", nativeQuery = true)
    int deleteReceivedBefore(@Param("before") Instant before);
}
