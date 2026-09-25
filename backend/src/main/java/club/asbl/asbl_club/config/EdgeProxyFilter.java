package club.asbl.asbl_club.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Visitors reach the API through the CDN's proxy (Cloudflare), then the host's own load balancer (Render):
//   browser ──► Cloudflare (public site address) ──► Render load balancer ──► Spring
// Without this filter Spring would see Render's address and Cloudflare's IP: requests from the site would look
// cross-origin (and be refused), links would point at Render, and every visitor would share one IP for rate
// limiting and the audit log.
//
// The proxy proves itself with a shared secret (like CloudFront's "origin custom header"). Only then is the
// request taken as the visitor's: their IP from X-Edge-Client-Ip, the site address from configuration (never
// from a header). Anything else, e.g. someone calling Render directly, is left exactly as it arrived.
// Runs first, so the rate limiter, security and audit all see the visitor.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(EdgeProxyProperties.class)
public class EdgeProxyFilter extends OncePerRequestFilter {

    static final String SECRET_HEADER = "X-Edge-Secret";
    static final String CLIENT_IP_HEADER = "X-Edge-Client-Ip";

    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private final byte[] secret;
    private final URI publicUrl;

    EdgeProxyFilter(EdgeProxyProperties properties) {
        this.secret = properties.enabled() ? properties.secret().getBytes(StandardCharsets.UTF_8) : null;
        this.publicUrl = properties.enabled() ? URI.create(properties.publicUrl()) : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(fromTrustedProxy(request) ? new VisitorRequest(request, publicUrl) : request, response);
    }

    private boolean fromTrustedProxy(HttpServletRequest request) {
        String sent = request.getHeader(SECRET_HEADER);
        // Constant-time comparison: the time taken mustn't reveal how much of a guess was right.
        return secret != null && sent != null
                && MessageDigest.isEqual(secret, sent.getBytes(StandardCharsets.UTF_8))
                && isIpAddress(request.getHeader(CLIENT_IP_HEADER));
    }

    private static boolean isIpAddress(String value) {
        return value != null && (IPV4.matcher(value).matches() || (value.contains(":") && IPV6.matcher(value).matches()));
    }

    // The request as the visitor made it: to the public site, from their own IP.
    private static final class VisitorRequest extends HttpServletRequestWrapper {

        private final String clientIp;
        private final URI site;

        VisitorRequest(HttpServletRequest request, URI site) {
            super(request);
            this.clientIp = request.getHeader(CLIENT_IP_HEADER);
            this.site = site;
        }

        @Override
        public String getRemoteAddr() {
            return clientIp;
        }

        @Override
        public String getRemoteHost() {
            return clientIp;
        }

        @Override
        public String getScheme() {
            return site.getScheme();
        }

        @Override
        public boolean isSecure() {
            return "https".equals(site.getScheme());
        }

        @Override
        public String getServerName() {
            return site.getHost();
        }

        @Override
        public int getServerPort() {
            return site.getPort() != -1 ? site.getPort() : isSecure() ? 443 : 80;
        }

        @Override
        public StringBuffer getRequestURL() {
            StringBuffer url = new StringBuffer(site.getScheme()).append("://").append(site.getHost());
            if (site.getPort() != -1) {
                url.append(':').append(site.getPort());
            }
            return url.append(getRequestURI());
        }
    }
}
