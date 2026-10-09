package club.asbl.asbl_club.finance;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.audit.AuditService;
import club.asbl.asbl_club.csv.ExcelCsv;
import club.asbl.asbl_club.finance.FinanceService.Kind;
import club.asbl.asbl_club.finance.FinanceService.Line;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// An association's finances, for the people who run its money: what came in for a year, what went back, the
// platform's commission, and every payment, on screen or as a CSV file for the accounts.
@RestController
@Tag(name = "Finances", description = "An association's payments, year by year")
class FinanceController {

    // As for the dues follow-up and the attendee list.
    private static final Set<String> FOLLOW_UP_ROLES = Set.of("ADMIN", "TREASURER");

    private final FinanceService financeService;
    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;
    private final AuditService auditService;
    private final MessageSource messages;

    FinanceController(FinanceService financeService, AsblService asblService, UserService userService,
            MembershipService membershipService, AuditService auditService, MessageSource messages) {
        this.financeService = financeService;
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
        this.auditService = auditService;
        this.messages = messages;
    }

    @Operation(operationId = "getFinances", summary = "A year of the association's payments, with its totals "
            + "(administrators and treasurers)", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "The totals and one page of payments, the most recent first",
            content = @Content(schema = @Schema(implementation = Report.class)))
    @ApiResponse(responseCode = "403", description = "Not an administrator or treasurer of this association",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping("/api/v1/asbls/{slug}/manage/finances")
    Report report(@PathVariable String slug,
            @Parameter(description = "Calendar year; the current one by default") @RequestParam(required = false)
            Integer year,
            @Parameter(description = "Page, from 0") @RequestParam(defaultValue = "0") int page,
            Authentication authentication) {
        Asbl asbl = forFollowUp(slug, userService.getAuthenticated(authentication));
        int shown = year(year);
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        FinanceService.Totals totals = financeService.totals(asbl, shown);
        List<Payment> payments = financeService.page(asbl, shown, page).stream().map(FinanceController::payment)
                .toList();
        int pages = Math.max(1, (totals.payments() + FinanceService.PAGE_SIZE - 1) / FinanceService.PAGE_SIZE);
        return new Report(shown, financeService.years(asbl), new Totals(totals.payments(), totals.tickets(),
                totals.dues(), totals.collected(), totals.refunded(), totals.commission(), totals.net()),
                payments, page, pages);
    }

    // The download is audited: personal data leaving the platform, in a file nobody can recall.
    @Operation(operationId = "exportFinances", summary = "A year of the association's payments, as a CSV file for "
            + "Excel (administrators and treasurers)", security = @SecurityRequirement(name = "bearer"))
    @ApiResponse(responseCode = "200", description = "Semicolon-separated, UTF-8 with a byte-order mark",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "403", description = "Not an administrator or treasurer of this association",
            content = @Content(mediaType = "application/problem+json"))
    @GetMapping(value = "/api/v1/asbls/{slug}/manage/finances/export", produces = "text/csv")
    ResponseEntity<byte[]> export(@PathVariable String slug,
            @Parameter(description = "Calendar year; the current one by default") @RequestParam(required = false)
            Integer year,
            Authentication authentication) {
        Asbl asbl = forFollowUp(slug, userService.getAuthenticated(authentication));
        int shown = year(year);
        Locale locale = LocaleContextHolder.getLocale();
        List<String> header = List.of("paidAt", "payer", "email", "kind", "description", "amount", "commission",
                "status", "refundedAt").stream().map(column -> text("finances.csv." + column, locale)).toList();
        List<List<String>> rows = financeService.all(asbl, shown).stream()
                .map(line -> List.of(ExcelCsv.when(line.paidAt()), ExcelCsv.text(line.payerName()),
                        ExcelCsv.text(line.payerEmail()), text("finances.kind." + line.kind(), locale),
                        ExcelCsv.text(description(line, locale)), ExcelCsv.amount(line.amount(), locale),
                        ExcelCsv.amount(line.commission(), locale),
                        text(line.refunded() ? "finances.status.REFUNDED" : "finances.status.PAID", locale),
                        ExcelCsv.when(line.refundedAt())))
                .toList();
        auditService.record("FINANCES_EXPORTED", asbl, "Asbl", asbl.getId(), Map.of("rows", rows.size(), "year",
                shown));
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("finances-" + slug + "-" + shown + ".csv").build().toString())
                .cacheControl(CacheControl.noStore()) // personal data: no copy in any cache
                .body(ExcelCsv.write(header, rows));
    }

    private int year(Integer year) {
        if (year == null) {
            return financeService.currentYear();
        }
        if (year < 2000 || year > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return year;
    }

    // "Concert — Standard", or "Dues 2026".
    private String description(Line line, Locale locale) {
        return line.kind() == Kind.TICKET
                ? line.event() + " — " + line.ticket()
                : text("finances.kind.DUES", locale) + " " + line.duesYear();
    }

    // Not a member, or a member without the role → 403.
    private Asbl forFollowUp(String slug, User user) {
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String role = membershipService.roleOf(user, asbl)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (!FOLLOW_UP_ROLES.contains(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return asbl;
    }

    private String text(String key, Locale locale) {
        return messages.getMessage(key, null, key, locale);
    }

    private static Payment payment(Line line) {
        return new Payment(line.id(), line.paidAt(), line.payerName(), line.payerEmail(), line.kind().name(),
                line.event(), line.ticket(), line.duesYear(), line.amount(), line.commission(),
                line.refunded() ? "REFUNDED" : "PAID", line.refundedAt());
    }

    @Schema(name = "FinanceTotals", description = "Amounts in euros, before Stripe's own fees")
    record Totals(@Schema(requiredMode = REQUIRED, description = "How many payments") int payments,
            @Schema(requiredMode = REQUIRED, description = "Paid for tickets") BigDecimal tickets,
            @Schema(requiredMode = REQUIRED, description = "Paid as membership dues") BigDecimal dues,
            @Schema(requiredMode = REQUIRED, description = "Tickets and dues") BigDecimal collected,
            @Schema(requiredMode = REQUIRED, description = "Given back to buyers") BigDecimal refunded,
            @Schema(requiredMode = REQUIRED, description = "Kept by the platform") BigDecimal commission,
            @Schema(requiredMode = REQUIRED, description = "Collected, less refunds and commission") BigDecimal net) {
    }

    @Schema(name = "FinancePayment")
    record Payment(@Schema(requiredMode = REQUIRED) long id,
            @Schema(requiredMode = REQUIRED) Instant paidAt,
            @Schema(requiredMode = REQUIRED) String payerName,
            @Schema(requiredMode = REQUIRED) String payerEmail,
            @Schema(requiredMode = REQUIRED, allowableValues = {"TICKET", "DUES"}) String kind,
            @Schema(description = "The event, for a ticket") String eventTitle,
            @Schema(description = "The ticket category, for a ticket") String ticketLabel,
            @Schema(description = "The year paid for, for dues") Integer duesYear,
            @Schema(requiredMode = REQUIRED, description = "Amount in euros") BigDecimal amount,
            @Schema(requiredMode = REQUIRED, description = "Kept by the platform, in euros (0 when refunded with "
                    + "the payment)") BigDecimal commission,
            @Schema(requiredMode = REQUIRED, allowableValues = {"PAID", "REFUNDED"}) String status,
            @Schema(description = "When it was refunded") Instant refundedAt) {
    }

    @Schema(name = "FinanceReport")
    record Report(@Schema(requiredMode = REQUIRED) int year,
            @Schema(requiredMode = REQUIRED, description = "Years with payments, and the current one, most recent "
                    + "first") List<Integer> years,
            @Schema(requiredMode = REQUIRED) Totals totals,
            @Schema(requiredMode = REQUIRED, description = "This page's payments, the most recent first")
            List<Payment> payments,
            @Schema(requiredMode = REQUIRED, description = "From 0") int page,
            @Schema(requiredMode = REQUIRED) int totalPages) {
    }
}
