package com.example.medrecords.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings bound from {@code app.jwt.*}.
 *
 * <p>The project deliberately uses a symmetric HS256 key so it can run without
 * an external identity provider. In a real deployment you would switch the
 * resource server to an OIDC issuer (Keycloak) and verify RS256 signatures
 * against the issuer's JWKS endpoint - see README.</p>
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl
) {

    public SecretKey secretKey() {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must contain at least 32 bytes (HS256 requirement)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
