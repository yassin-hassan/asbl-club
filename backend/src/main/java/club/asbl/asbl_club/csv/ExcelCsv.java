package club.asbl.asbl_club.csv;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

// Spreadsheet files made for Excel as Belgian associations use it: semicolons between columns and a decimal comma
// (Excel's defaults in French and Dutch), UTF-8 with a byte-order mark (without it, Excel garbles accents), times in
// Belgian time. Quoting is Apache Commons CSV's (RFC 4180). Shared by every export, so the protection below can't be
// forgotten in one of them.
public final class ExcelCsv {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String FORMULA_STARTS = "=+-@\t\r";

    private ExcelCsv() {
    }

    public static byte[] write(List<String> header, List<List<String>> rows) {
        CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(';').setRecordSeparator("\r\n").get();
        StringWriter out = new StringWriter().append('﻿');
        try (CSVPrinter csv = new CSVPrinter(out, format)) {
            csv.printRecord(header);
            for (List<String> row : rows) {
                csv.printRecord(row);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e); // a StringWriter doesn't fail
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    // CSV injection (OWASP): a cell starting with = + - @ (or a tab / carriage return) is run as a formula when the
    // file is opened in Excel or LibreOffice. A person named =HYPERLINK("https://evil…") could make an administrator's
    // spreadsheet send data away. A leading apostrophe makes the cell plain text. Applied to everything people type.
    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    public static String amount(BigDecimal amount, Locale locale) {
        if (amount == null) {
            return "";
        }
        char decimal = DecimalFormatSymbols.getInstance(locale).getDecimalSeparator();
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', decimal);
    }

    public static String when(Instant instant) {
        return instant == null ? "" : WHEN.format(instant.atZone(BRUSSELS));
    }
}
