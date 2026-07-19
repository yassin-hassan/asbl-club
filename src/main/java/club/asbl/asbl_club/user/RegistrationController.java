package club.asbl.asbl_club.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AutoLogin autoLogin;

    RegistrationController(UserService userService, AutoLogin autoLogin) {
        this.userService = userService;
        this.autoLogin = autoLogin;
    }

    @GetMapping("/register")
    String showForm(Model model) {
        model.addAttribute("form", new RegistrationForm("", "", ""));
        return "register";
    }

    @PostMapping("/register")
    String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult bindingResult,
            HttpServletRequest request, HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form.name(), form.email(), form.password());
        } catch (EmailAlreadyUsedException e) {
            bindingResult.rejectValue("email", "email.duplicate", "This email address is already in use");
            return "register";
        }
        autoLogin.login(form.email(), request, response);
        return "redirect:/";
    }
}
