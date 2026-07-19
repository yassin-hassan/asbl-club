package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.tenant.TenantContext;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
class AsblController {

    private final AsblService asblService;
    private final UserService userService;
    private final MembershipService membershipService;

    AsblController(AsblService asblService, UserService userService, MembershipService membershipService) {
        this.asblService = asblService;
        this.userService = userService;
        this.membershipService = membershipService;
    }

    @GetMapping("/asbls/new")
    String showForm(Model model) {
        model.addAttribute("form", new CreateAsblForm("", "", "", "fr"));
        return "asbl/create";
    }

    @PostMapping("/asbls")
    String create(@Valid @ModelAttribute("form") CreateAsblForm form, BindingResult bindingResult,
            Authentication authentication) {
        if (bindingResult.hasErrors()) {
            return "asbl/create";
        }
        User creator = userService.getByEmail(authentication.getName());
        try {
            asblService.createAsbl(creator, form.denomination(), form.bceNumber(), form.slug(), form.defaultLanguage());
        } catch (SlugAlreadyUsedException e) {
            bindingResult.rejectValue("slug", "asbl.slug.duplicate", "This URL identifier is already taken");
            return "asbl/create";
        } catch (BceAlreadyUsedException e) {
            bindingResult.rejectValue("bceNumber", "asbl.bce.duplicate", "This BCE number is already registered");
            return "asbl/create";
        }
        return "redirect:/";
    }

    @GetMapping("/asbls/{slug}/members")
    String members(@PathVariable String slug, Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isMember(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        TenantContext.set(asbl.getId());
        try {
            model.addAttribute("asbl", asbl);
            model.addAttribute("members", membershipService.membersOf(asbl));
            return "asbl/members";
        } finally {
            TenantContext.clear();
        }
    }
}
