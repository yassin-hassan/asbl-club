package club.asbl.asbl_club.account;

import club.asbl.asbl_club.asbl.AsblSummary;
import club.asbl.asbl_club.payment.PaymentExport;
import java.util.List;

public record AccountExport(
        ProfileExport profile,
        List<AsblSummary> memberships,
        List<PaymentExport> payments) {
}
