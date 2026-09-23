package club.asbl.asbl_club.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AddTicketCategoryForm(

        @NotBlank
        @Size(max = 100)
        String label,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 6, fraction = 2)
        BigDecimal price,

        @NotNull
        @Min(1)
        Integer totalSeats) {
}
