package club.asbl.asbl_club.event;

import java.time.Instant;

public record EventSummary(Long id, String title, Instant startsAt, String status, String visibility) {
}
