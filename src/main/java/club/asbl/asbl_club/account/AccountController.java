package club.asbl.asbl_club.account;

import club.asbl.asbl_club.user.User;
import club.asbl.asbl_club.user.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class AccountController {

    private final UserService userService;
    private final AccountService accountService;

    AccountController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    @GetMapping("/account")
    String account() {
        return "account/index";
    }

    @GetMapping("/account/export")
    @ResponseBody
    ResponseEntity<AccountExport> export(Authentication authentication) {
        User user = userService.getByEmail(authentication.getName());
        AccountExport data = accountService.exportFor(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mes-donnees.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }

    @PostMapping("/account/delete")
    String delete(Authentication authentication, HttpServletRequest request) throws ServletException {
        User user = userService.getByEmail(authentication.getName());
        accountService.deleteAccount(user);
        request.logout();
        return "redirect:/login?deleted";
    }
}
