package club.asbl.asbl_club.payment;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.MessageSource;

// The attendee list as a spreadsheet file, made for Excel as Belgian associations use it: semicolons between
// columns and a decimal comma (Excel's defaults in French and Dutch), UTF-8 with a byte-order mark (without it, Excel
// garbles accents), headers and statuses in the reader's language. Quoting is Apache Commons CSV's (RFC 4180).
final class AttendeeCsv {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private AttendeeCsv() {
    }

    static byte[] write(List<Attendees.Attendee> attendees, MessageSource messages, Locale locale) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setRecordSeparator("\r\n").get();
        char decimal = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        StringWriter out = new StringWriter().append('﻿');
        try (CSVPrinter csv = new CSVPrinter(out, format)) {
            csv.printRecord(header("name", messages, locale), header("email", messages, locale),
                    header("ticket", messages, locale), header("status", messages, locale),
                    header("amount", messages, locale), header("bookedAt", messages, locale));
            for (Attendees.Attendee a : attendees) {
                csv.printRecord(text(a.name()), text(a.email()), text(a.ticket()),
                        messages.getMessage("attendees.status." + a.status(), null, a.status(), locale),
                        amount(a.amount(), decimal), a.bookedAt() == null ? "" : WHEN.format(a.bookedAt().atZone(BRUSSELS)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // a StringWriter doesn't fail
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    // CSV injection (OWASP): a cell starting with = + - @ (or a tab / carriage return) is run as a formula when the
    // file is opened in Excel or LibreOffice. A booker named =HYPERLINK("https://evil…") could make an administrator's
    // spreadsheet send data away. A leading apostrophe makes the cell plain text. Applied to everything people type.
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static String amount(BigDecimal amount, char decimal) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', decimal);
    }

    private static String header(String column, MessageSource messages, Locale locale) {
        return messages.getMessage("attendees.csv." + column, null, column, locale);
    }
}
