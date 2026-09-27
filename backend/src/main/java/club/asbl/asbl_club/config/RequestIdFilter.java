package club.asbl.asbl_club.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// One ID per request, on every log line written while handling it (SLF4J's MDC: a per-thread map the log format
// includes), and in the X-Request-Id response header. "Request abc… failed" then leads straight to its log lines.
//
// The Cloudflare Worker starts each request with a new ID and sends it along; one arriving any other way, or not
// shaped like a UUID, is replaced (a client can't write into our logs: no line breaks, no fake fields).
// Runs before every other filter, so their log lines carry the ID too; ends with one line per request.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Pattern UUID_SHAPE =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdOf(request);
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (!request.getRequestURI().startsWith("/actuator/health")) { // the host's health checks: noise
                // Path without the query string: nothing a visitor typed beyond the route ends up in the logs.
                // Also as fields, for searching the JSON logs (status >= 500, durationMs > 1000…).
                long durationMs = (System.nanoTime() - start) / 1_000_000;
                log.atInfo()
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("path", request.getRequestURI())
                        .addKeyValue("status", response.getStatus())
                        .addKeyValue("durationMs", durationMs)
                        .log("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(),
                                response.getStatus(), durationMs);
            }
            MDC.remove(MDC_KEY); // threads are reused for other requests
        }
    }

    private static String requestIdOf(HttpServletRequest request) {
        String sent = request.getHeader(HEADER);
        return sent != null && UUID_SHAPE.matcher(sent).matches() ? sent : UUID.randomUUID().toString();
    }
}
