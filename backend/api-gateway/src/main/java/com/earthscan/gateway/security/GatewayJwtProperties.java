package com.earthscan.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Mirrors {@code earthscan.jwt.*} so the gateway can verify tokens minted by auth-service. */
@ConfigurationProperties(prefix = "earthscan.jwt")
public class GatewayJwtProperties {

    private String secret = "";
    private String issuer = "EarthScanBackend";
    private String audience = "EarthScanUsers";

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
}
