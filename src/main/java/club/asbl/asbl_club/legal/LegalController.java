package club.asbl.asbl_club.legal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class LegalController {

    @GetMapping("/legal")
    String legalNotice() {
        return "legal/notice";
    }

    @GetMapping("/privacy")
    String privacy() {
        return "legal/privacy";
    }

    @GetMapping("/cookies")
    String cookies() {
        return "legal/cookies";
    }
}
