package club.asbl.asbl_club.audit;

import club.asbl.asbl_club.asbl.Asbl;
import club.asbl.asbl_club.asbl.AsblService;
import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
class AuditController {

    private final AuditService auditService;
    private final AsblService asblService;
    private final MembershipService membershipService;
    private final UserService userService;

    AuditController(AuditService auditService, AsblService asblService,
            MembershipService membershipService, UserService userService) {
        this.auditService = auditService;
        this.asblService = asblService;
        this.membershipService = membershipService;
        this.userService = userService;
    }

    @GetMapping("/asbls/{slug}/audit")
    String journal(@PathVariable String slug, Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("asbl", asbl);
        model.addAttribute("entries", auditService.journalOf(asbl));
        return "audit/journal";
    }

    @GetMapping("/admin/audit")
    String adminJournal(Model model) {
        model.addAttribute("entries", auditService.journalAll());
        return "audit/admin-journal";
    }
}
