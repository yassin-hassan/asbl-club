package club.asbl.asbl_club.event;

import java.math.BigDecimal;

public record TicketCategorySummary(Long id, String label, BigDecimal price, int totalSeats, int soldSeats) {
}
