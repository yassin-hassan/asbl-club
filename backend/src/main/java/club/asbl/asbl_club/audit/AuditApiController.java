package club.asbl.asbl_club.audit;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// The audit journals, read-only: an association's own (its administrators) and the whole platform's
// (super-administrators; the URL rule in SecurityConfig guards all of /api/v1/admin/**).
// Paged: a journal only grows (kept 3 years), so it is never sent whole.
@RestController
@Tag(name = "Audit", description = "Audit journals (administrators)")
class AuditApiController {

    static final int PAGE_SIZE = 50;

    private final AuditService auditService;
    private final AsblService asblService;
    private final MembershipService membershipService;
    private final UserService userService;

    AuditApiController(AuditService auditService, AsblService asblService, MembershipService membershipService,
            UserService userService) {
        this.auditService = auditService;
        this.asblService = asblService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @Operation(operationId = "getAsblAuditJournal", summary = "What happened in the association (administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/asbls/{slug}/manage/audit")
    AuditJournal asblJournal(@PathVariable String slug,
            @Parameter(description = "0-based page number") @RequestParam(defaultValue = "0") int page,
            Authentication authentication) {
        User user = userService.getAuthenticated(authentication);
        Asbl asbl = asblService.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return journal(asbl.getDenomination(), auditService.journalOf(asbl, Math.max(page, 0), PAGE_SIZE),
                e -> new AuditJournal.Entry(e.createdAt(), e.action(), e.actorEmail(), e.asbl(), e.entityType(),
                        e.entityId(), null));
    }

    @Operation(operationId = "getPlatformAuditJournal", summary = "Everything that happened on the platform (super-administrators)",
            security = @SecurityRequirement(name = "bearer"))
    @GetMapping("/api/v1/admin/audit")
    AuditJournal platformJournal(
            @Parameter(description = "0-based page number") @RequestParam(defaultValue = "0") int page) {
        return journal(null, auditService.journalAll(Math.max(page, 0), PAGE_SIZE),
                e -> new AuditJournal.Entry(e.createdAt(), e.action(), e.actorEmail(), e.asbl(), e.entityType(),
                        e.entityId(), e.ip()));
    }

    private static AuditJournal journal(String denomination, Page<AuditLogView> page,
            Function<AuditLogView, AuditJournal.Entry> toEntry) {
        return new AuditJournal(denomination, page.map(toEntry).getContent(), page.getNumber(), page.getTotalPages(),
                page.getTotalElements());
    }
}
