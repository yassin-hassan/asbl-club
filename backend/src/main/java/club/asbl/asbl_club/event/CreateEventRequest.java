package club.asbl.asbl_club.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

// startsAt is an exact moment (ISO 8601 with an offset, e.g. 2026-12-01T19:00:00Z): the browser converts what the
// user typed from its own time zone, so the server's time zone never matters.
record CreateEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 5000) String description,
        @NotNull Instant startsAt,
        @Size(max = 255) String location,
        @NotBlank @Pattern(regexp = "PUBLIC|MEMBERS") String visibility) {
}
