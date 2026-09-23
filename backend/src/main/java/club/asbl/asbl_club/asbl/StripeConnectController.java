package club.asbl.asbl_club.asbl;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import com.stripe.exception.StripeException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
class StripeConnectController {

    private final AsblService asblService;
    private final MembershipService membershipService;
    private final UserService userService;
    private final StripeConnectService stripeConnectService;

    StripeConnectController(AsblService asblService, MembershipService membershipService,
            UserService userService, StripeConnectService stripeConnectService) {
        this.asblService = asblService;
        this.membershipService = membershipService;
        this.userService = userService;
        this.stripeConnectService = stripeConnectService;
    }

    @GetMapping("/asbls/{slug}/stripe/connect")
    String connect(@PathVariable String slug, Authentication authentication) throws StripeException {
        Asbl asbl = resolveForAdmin(slug, authentication);
        return "redirect:" + startOnboarding(asbl, slug);
    }

    @GetMapping("/asbls/{slug}/stripe/refresh")
    String refresh(@PathVariable String slug, Authentication authentication) throws StripeException {
        Asbl asbl = resolveForAdmin(slug, authentication);
        return "redirect:" + startOnboarding(asbl, slug);
    }

    @GetMapping("/asbls/{slug}/stripe/return")
    String returnFromStripe(@PathVariable String slug, Model model, Authentication authentication)
            throws StripeException {
        Asbl asbl = resolveForAdmin(slug, authentication);
        model.addAttribute("asbl", asbl);
        model.addAttribute("ready", stripeConnectService.isReady(asbl));
        return "asbl/stripe-status";
    }

    private String startOnboarding(Asbl asbl, String slug) throws StripeException {
        String base = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/asbls/" + slug + "/stripe").toUriString();
        return stripeConnectService.startOnboarding(asbl, base + "/refresh", base + "/return");
    }

    private Asbl resolveForAdmin(String slug, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        Asbl asbl = asblService.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!membershipService.isAdmin(user, asbl)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return asbl;
    }
}
