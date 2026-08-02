package com.earthscan.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised JWT settings. Every service shares the same symmetric secret so any service can
 * validate a token minted by auth-service without a network round trip.
 */
@ConfigurationProperties(prefix = "earthscan.jwt")
public class JwtProperties {

    /** HMAC-SHA signing secret. Must be at least 32 characters for HS256. */
    private String secret = "";

    /** Expected {@code iss} claim. */
    private String issuer = "EarthScanBackend";

    /** Expected {@code aud} claim. */
    private String audience = "EarthScanUsers";

    /** Token lifetime in milliseconds. Defaults to 7 days. */
    private long expirationMs = 604_800_000L;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
