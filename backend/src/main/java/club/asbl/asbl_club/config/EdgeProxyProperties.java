package club.asbl.asbl_club.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// The CDN in front of the API (Cloudflare Pages): the secret its proxy function sends, and the public address
// visitors use. Both empty = no CDN in front; requests are taken as they arrive.
@ConfigurationProperties("edge")
public record EdgeProxyProperties(String secret, String publicUrl) {

    public boolean enabled() {
        return secret != null && !secret.isBlank() && publicUrl != null && !publicUrl.isBlank();
    }
}
