package club.asbl.asbl_club.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// The site moved: people opening a page on the server's own address (old links and bookmarks from when
// the server rendered the pages itself) are sent to the same page on the public site, which the CDN serves (see EdgeProxyFilter).
//
// Not redirected:
// - requests the CDN relayed to us: they already look like the public site (EdgeProxyFilter), and redirecting
//   them would send the CDN's own requests (API calls, RSS feeds) round in circles;
// - machine endpoints called on this address directly: the API, Stripe's webhooks, the platform's health check,
//   the public signing keys.
// 302 (temporary) while the new site settles in; browsers cache a 301 for good.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5) // after EdgeProxyFilter, before the rate limiter and security
@EnableConfigurationProperties(EdgeProxyProperties.class)
public class RedirectToSiteFilter extends OncePerRequestFilter {

    private static final List<String> STAY_HERE = List.of("/api/", "/webhooks/", "/actuator/", "/.well-known/");

    private final URI site;

    RedirectToSiteFilter(EdgeProxyProperties properties) {
        this.site = properties.enabled() ? URI.create(properties.publicUrl()) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!shouldRedirect(request)) {
            chain.doFilter(request, response);
            return;
        }
        String query = request.getQueryString();
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", site.getScheme() + "://" + site.getRawAuthority() + request.getRequestURI()
                + (query == null ? "" : "?" + query));
    }

    private boolean shouldRedirect(HttpServletRequest request) {
        if (site == null || !("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            return false; // no public site configured (local development), or not a page visit
        }
        if (isTheSite(request)) {
            return false; // already the public site (relayed by the CDN)
        }
        String path = request.getRequestURI();
        return STAY_HERE.stream().noneMatch(path::startsWith);
    }

    // Host and port: locally the site and the server can share "localhost" and differ only by port.
    private boolean isTheSite(HttpServletRequest request) {
        int sitePort = site.getPort() != -1 ? site.getPort() : "https".equals(site.getScheme()) ? 443 : 80;
        return site.getHost().equalsIgnoreCase(request.getServerName()) && sitePort == request.getServerPort();
    }
}
