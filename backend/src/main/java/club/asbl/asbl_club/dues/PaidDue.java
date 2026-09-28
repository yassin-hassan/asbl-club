package club.asbl.asbl_club.dues;

import java.math.BigDecimal;
import java.time.Instant;

// Someone's dues whose payment went through: who, how much, when.
public record PaidDue(Long userId, BigDecimal amount, Instant paidAt) {
}
