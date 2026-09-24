package club.asbl.asbl_club.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Publishes the PUBLIC half of the signing key(s) in the standard JWK Set format, at the standard path.
// Anything that needs to verify our tokens (another service, a gateway) can fetch it instead of being handed
// a key; during a key rotation it would list both the old and the new key.
@RestController
class JwksController {

    private final Map<String, Object> jwks;

    JwksController(RSAKey jwtSigningKey) {
        // toPublicJWK() drops every private component; only the public key and its ID are published.
        this.jwks = new JWKSet(jwtSigningKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> jwks() {
        return jwks;
    }
}
