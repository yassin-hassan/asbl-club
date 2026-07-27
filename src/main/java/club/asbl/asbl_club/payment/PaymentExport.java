package club.asbl.asbl_club.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentExport(BigDecimal amount, String status, Instant paidAt) {
}
