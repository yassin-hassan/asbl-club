package club.asbl.asbl_club.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// rate-limit.enabled: on in the application, off in the test suite (its many logins would trip it).
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(boolean enabled) {
}
