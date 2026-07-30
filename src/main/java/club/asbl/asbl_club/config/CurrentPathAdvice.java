package club.asbl.asbl_club.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the current request path to every view so the shared header can build
 * language-switch links that stay on the same page instead of jumping home.
 */
@ControllerAdvice
class CurrentPathAdvice {

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
