package club.asbl.asbl_club.asbl;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAsblForm(

        @NotBlank
        @Size(max = 255)
        String denomination,

        @NotBlank
        @Pattern(regexp = "\\d{4}\\.\\d{3}\\.\\d{3}", message = "{asbl.bceNumber.pattern}")
        String bceNumber,

        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "[a-z0-9-]+", message = "{asbl.slug.pattern}")
        String slug,

        @NotBlank
        @Pattern(regexp = "fr|nl|en", message = "{asbl.defaultLanguage.pattern}")
        String defaultLanguage) {
}
