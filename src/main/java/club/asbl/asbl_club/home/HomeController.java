package club.asbl.asbl_club.home;

import club.asbl.asbl_club.membership.MembershipService;
import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

    private final UserService userService;
    private final MembershipService membershipService;

    HomeController(UserService userService, MembershipService membershipService) {
        this.userService = userService;
        this.membershipService = membershipService;
    }

    @GetMapping("/")
    String home(Model model, Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        model.addAttribute("asbls", membershipService.membershipsOf(user));
        return "home";
    }
}
