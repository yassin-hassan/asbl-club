package club.asbl.asbl_club.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(String secretKey, String publishableKey, String webhookSecret) {
}
