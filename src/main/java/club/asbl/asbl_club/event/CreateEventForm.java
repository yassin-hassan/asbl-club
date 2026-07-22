package club.asbl.asbl_club.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public record CreateEventForm(

        @NotBlank
        @Size(max = 255)
        String title,

        @Size(max = 5000)
        String description,

        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime startsAt,

        @Size(max = 255)
        String location,

        @NotBlank
        @Pattern(regexp = "PUBLIC|MEMBERS")
        String visibility) {
}
