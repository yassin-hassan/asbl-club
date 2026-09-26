package club.asbl.asbl_club.payment;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Stripe stops retrying an event after 3 days; a month of records leaves a wide margin (and a trace for support).
@Component
class WebhookRetention {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetention.class);
    static final Duration KEEP = Duration.ofDays(30);

    private final ProcessedWebhookEventRepository processedEvents;

    WebhookRetention(ProcessedWebhookEventRepository processedEvents) {
        this.processedEvents = processedEvents;
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    void purgeOldEvents() {
        int removed = processedEvents.deleteReceivedBefore(Instant.now().minus(KEEP));
        if (removed > 0) {
            log.info("Purged {} processed Stripe events older than {} days", removed, KEEP.toDays());
        }
    }
}
