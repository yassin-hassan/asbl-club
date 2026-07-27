package club.asbl.asbl_club.event;

import java.time.Instant;

public record EventFeedItem(Long id, String title, String description, Instant startsAt) {
}
