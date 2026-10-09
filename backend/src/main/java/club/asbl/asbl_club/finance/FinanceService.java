package club.asbl.asbl_club.finance;

import club.asbl.asbl_club.asbl.Asbl;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// What came into an association's Stripe account, for its treasurer: the money that came in (tickets, dues), what
// went back to buyers, and the platform's commission. Only payments that went through count (paid or refunded since);
// a year is a calendar year in Belgian time, by the day the payment went through. Stripe's own fees aren't here:
// Stripe takes them from the association's account and shows them there.
@Service
@Transactional(readOnly = true)
public class FinanceService {

    public static final int PAGE_SIZE = 50;
    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");

    // The payments of an association and a year, with what each one was for. The commission is what the platform
    // kept: none when it went back with a refund.
    private static final String PAYMENTS = """
            SELECT p.id, p.paid_at, p.payer_name, p.payer_email, pa.type, e.title AS event, tc.label AS ticket,
                   d.year AS dues_year, p.amount, p.status, p.refunded_at,
                   CASE WHEN p.status = 'REFUNDED' AND p.commission_refunded THEN 0.00 ELSE p.commission END AS commission
            FROM payments p
            JOIN payables pa ON pa.id = p.payable_id
            LEFT JOIN registrations r ON r.id = p.payable_id
            LEFT JOIN events e ON e.id = r.event_id
            LEFT JOIN ticket_categories tc ON tc.id = r.ticket_category_id
            LEFT JOIN dues d ON d.id = p.payable_id
            WHERE p.asbl_id = ? AND p.status IN ('SUCCEEDED', 'REFUNDED')
              AND extract(year FROM p.paid_at AT TIME ZONE 'Europe/Brussels') = ?
            ORDER BY p.paid_at DESC, p.id DESC""";

    private final JdbcTemplate jdbc;

    FinanceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int currentYear() {
        return LocalDate.now(BRUSSELS).getYear();
    }

    // The years with payments, the most recent first; the current one always (nothing in it yet is an answer too).
    public List<Integer> years(Asbl asbl) {
        TreeSet<Integer> years = new TreeSet<>(jdbc.queryForList("""
                SELECT DISTINCT extract(year FROM paid_at AT TIME ZONE 'Europe/Brussels')::int FROM payments
                WHERE asbl_id = ? AND status IN ('SUCCEEDED', 'REFUNDED')""", Integer.class, asbl.getId()));
        years.add(currentYear());
        return List.copyOf(years.descendingSet());
    }

    public Totals totals(Asbl asbl, int year) {
        return jdbc.queryForObject("""
                SELECT count(*) AS payments,
                       coalesce(sum(amount) FILTER (WHERE type = 'REGISTRATION'), 0) AS tickets,
                       coalesce(sum(amount) FILTER (WHERE type = 'MEMBERSHIP'), 0) AS dues,
                       coalesce(sum(amount) FILTER (WHERE status = 'REFUNDED'), 0) AS refunded,
                       coalesce(sum(commission), 0) AS commission
                FROM (%s) payments""".formatted(PAYMENTS), (rs, n) -> {
            BigDecimal tickets = rs.getBigDecimal("tickets");
            BigDecimal dues = rs.getBigDecimal("dues");
            BigDecimal refunded = rs.getBigDecimal("refunded");
            BigDecimal commission = rs.getBigDecimal("commission");
            BigDecimal collected = tickets.add(dues);
            return new Totals(rs.getInt("payments"), tickets, dues, collected, refunded, commission,
                    collected.subtract(refunded).subtract(commission));
        }, asbl.getId(), year);
    }

    // One page (from 0), the most recent first.
    public List<Line> page(Asbl asbl, int year, int page) {
        return jdbc.query(PAYMENTS + " LIMIT ? OFFSET ?", FinanceService::line, asbl.getId(), year, PAGE_SIZE,
                (long) page * PAGE_SIZE);
    }

    // Every payment of the year, for the export.
    public List<Line> all(Asbl asbl, int year) {
        return jdbc.query(PAYMENTS, FinanceService::line, asbl.getId(), year);
    }

    private static Line line(ResultSet rs, int n) throws SQLException {
        boolean ticket = "REGISTRATION".equals(rs.getString("type"));
        Integer duesYear = rs.getObject("dues_year", Integer.class);
        return new Line(rs.getLong("id"), instant(rs.getTimestamp("paid_at")), rs.getString("payer_name"),
                rs.getString("payer_email"), ticket ? Kind.TICKET : Kind.DUES, rs.getString("event"),
                rs.getString("ticket"), duesYear, rs.getBigDecimal("amount"), rs.getBigDecimal("commission"),
                "REFUNDED".equals(rs.getString("status")), instant(rs.getTimestamp("refunded_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public enum Kind { TICKET, DUES }

    // collected = tickets + dues; net = collected - refunded - commission (before Stripe's fees).
    public record Totals(int payments, BigDecimal tickets, BigDecimal dues, BigDecimal collected, BigDecimal refunded,
            BigDecimal commission, BigDecimal net) {
    }

    public record Line(long id, Instant paidAt, String payerName, String payerEmail, Kind kind, String event,
            String ticket, Integer duesYear, BigDecimal amount, BigDecimal commission, boolean refunded,
            Instant refundedAt) {
    }
}
