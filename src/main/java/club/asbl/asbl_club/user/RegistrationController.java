package club.asbl.asbl_club.user;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
class RegistrationController {

    private final UserService userService;

    RegistrationController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/register")
    String showForm(Model model) {
        model.addAttribute("form", new RegistrationForm("", "", ""));
        return "register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form.name(), form.email(), form.password());
        } catch (EmailAlreadyUsedException e) {
            bindingResult.rejectValue("email", "email.duplicate", "This email address is already in use");
            return "register";
        }
        return "redirect:/login?registered";
    }
}
