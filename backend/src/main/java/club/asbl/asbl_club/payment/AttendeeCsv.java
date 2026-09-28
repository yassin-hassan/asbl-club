package club.asbl.asbl_club.payment;

import club.asbl.asbl_club.csv.ExcelCsv;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;

// The attendee list as a spreadsheet file (see ExcelCsv for the Excel conventions and the formula protection),
// headers and statuses in the reader's language.
final class AttendeeCsv {

    private AttendeeCsv() {
    }

    static byte[] write(List<Attendees.Attendee> attendees, MessageSource messages, Locale locale) {
        List<String> header = List.of(header("name", messages, locale), header("email", messages, locale),
                header("ticket", messages, locale), header("status", messages, locale),
                header("amount", messages, locale), header("bookedAt", messages, locale));
        List<List<String>> rows = attendees.stream()
                .map(a -> List.of(ExcelCsv.text(a.name()), ExcelCsv.text(a.email()), ExcelCsv.text(a.ticket()),
                        messages.getMessage("attendees.status." + a.status(), null, a.status(), locale),
                        ExcelCsv.amount(a.amount(), locale), ExcelCsv.when(a.bookedAt())))
                .toList();
        return ExcelCsv.write(header, rows);
    }

    private static String header(String column, MessageSource messages, Locale locale) {
        return messages.getMessage("attendees.csv." + column, null, column, locale);
    }
}
