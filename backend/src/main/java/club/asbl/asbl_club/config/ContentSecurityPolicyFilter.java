package club.asbl.asbl_club.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

/**
 * Emits a Content-Security-Policy header with a fresh per-request nonce.
 *
 * <p>The nonce is also exposed as the {@code cspNonce} request attribute so our own
 * inline scripts can carry it (see the Thymeleaf templates). Injected scripts cannot
 * know the nonce, so the browser refuses to run them — a second line of defence
 * against XSS on top of Thymeleaf's escaping.
 */
@Component
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    static final String NONCE_ATTRIBUTE = "cspNonce";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String nonce = newNonce();
        request.setAttribute(NONCE_ATTRIBUTE, nonce);
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; "
                        + "script-src 'self' 'nonce-" + nonce + "' https://js.stripe.com; "
                        + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                        + "font-src 'self' https://fonts.gstatic.com; "
                        + "img-src 'self' data:; "
                        + "connect-src 'self' https://api.stripe.com; "
                        + "frame-src https://js.stripe.com https://hooks.stripe.com; "
                        + "frame-ancestors 'self'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'; "
                        + "object-src 'none'");
        chain.doFilter(request, response);
    }

    private static String newNonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }
}
