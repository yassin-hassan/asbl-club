package club.asbl.asbl_club.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Exposes the current request path to every rendered view so the shared header
 * can build language-switch links that stay on the same page. Implemented as an
 * interceptor rather than a {@code @ControllerAdvice} so it does not interfere
 * with the OpenAPI document generation.
 */
class CurrentPathInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) {
        if (modelAndView == null) {
            return;
        }
        String viewName = modelAndView.getViewName();
        if (viewName == null || !viewName.startsWith("redirect:")) {
            modelAndView.addObject("currentPath", request.getRequestURI());
        }
    }
}
