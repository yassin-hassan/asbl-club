package club.asbl.asbl_club.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Limits how often one client IP may call the credential endpoints (brute force, credential stuffing,
 * mass sign-ups). Token bucket per (endpoint, IP): short bursts are fine, the sustained rate is capped.
 * Over the limit: 429 Too Many Requests with a Retry-After header.
 *
 * <p>Runs before Spring Security: form login is handled inside the security filter chain and never
 * reaches later filters. Counters live in this instance's memory; with several instances each counts
 * separately (a shared store such as Redis would be needed then).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(RateLimitProperties.class)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    // POST endpoint -> requests allowed per minute per client IP
    private static final Map<String, Integer> LIMITS_PER_MINUTE = Map.of(
            "/api/v1/auth/login", 10,
            "/login", 10,
            "/register", 5,
            "/api/v1/auth/refresh", 30); // every page load of the Angular app refreshes once

    private final boolean enabled;

    // Bounded and expiring: a plain map would grow with every IP ever seen, which an attacker
    // could use to exhaust memory.
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    AuthRateLimitFilter(RateLimitProperties properties) {
        this.enabled = properties.enabled();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || !"POST".equals(request.getMethod()) || !LIMITS_PER_MINUTE.containsKey(pathOf(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = pathOf(request);
        Bucket bucket = buckets.get(path + " " + request.getRemoteAddr(),
                key -> newBucket(LIMITS_PER_MINUTE.get(path)));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    // The decoded, normalised path the application routes on. The raw URI would let "/api/v1/auth/%6cogin"
    // slip past the limit while still reaching the login endpoint.
    private static String pathOf(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                .build();
    }
}
