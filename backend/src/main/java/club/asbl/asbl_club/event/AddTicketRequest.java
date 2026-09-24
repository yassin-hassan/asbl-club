package club.asbl.asbl_club.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// Same rules as the server-rendered form: a price in euros with at most 2 decimals, at least one seat.
record AddTicketRequest(
        @NotBlank @Size(max = 100) String label,
        @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal price,
        @NotNull @Min(1) Integer totalSeats) {
}
