package com.earthscan.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verifies signature, issuer, audience and expiry. Nothing more.
 *
 * <p>Kept separate from the servlet-side {@code JwtTokenProvider} in common-lib because pulling that
 * class in would drag {@code spring-boot-starter-web} onto the gateway's classpath, and a servlet
 * stack alongside WebFlux stops Spring Cloud Gateway from starting.</p>
 */
@Component
public class GatewayTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(GatewayTokenValidator.class);

    private final GatewayJwtProperties properties;
    private final SecretKey signingKey;

    public GatewayTokenValidator(GatewayJwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "earthscan.jwt.secret must be at least 32 bytes long for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public Optional<TokenPayload> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!audienceMatches(claims)) {
                return Optional.empty();
            }

            List<String> roles = List.of();
            Object rawRoles = claims.get("roles");
            if (rawRoles instanceof Collection<?> values) {
                roles = values.stream().map(String::valueOf).toList();
            }
            return Optional.of(new TokenPayload(claims.getSubject(), roles));
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Gateway rejected token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean audienceMatches(Claims claims) {
        Object audience = claims.get("aud");
        if (audience instanceof Collection<?> values) {
            return values.stream().anyMatch(v -> properties.getAudience().equals(String.valueOf(v)));
        }
        return audience != null && properties.getAudience().equals(String.valueOf(audience));
    }

    /** The two claims the gateway forwards downstream for logging purposes. */
    public record TokenPayload(String subject, List<String> roles) {
    }
}
