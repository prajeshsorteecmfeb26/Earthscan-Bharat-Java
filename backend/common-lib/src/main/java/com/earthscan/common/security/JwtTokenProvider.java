package com.earthscan.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mints and validates the HS256 access tokens used across the platform.
 *
 * <p>auth-service is the only service that calls {@link #generateToken}; every other service only
 * calls {@link #parse}. Because the secret is symmetric and shared, validation is a local CPU
 * operation with no network hop, which is what keeps the per-request cost of a microservice split
 * acceptable.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_EMAIL = "email";
    private static final int MIN_SECRET_LENGTH = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "earthscan.jwt.secret must be at least " + MIN_SECRET_LENGTH
                            + " bytes long for HS256. Configure it via the JWT_SECRET environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a signed token for the given user. Called only by auth-service on login. */
    public String generateToken(Long userId, String name, String email, Collection<RoleName> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.getExpirationMs());
        List<String> roleNames = roles.stream().map(Enum::name).toList();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuer(properties.getIssuer())
                .audience().add(properties.getAudience()).and()
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLES, roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Verifies the signature, issuer, audience and expiry of a token and maps it to a principal.
     *
     * @return the principal, or {@link Optional#empty()} when the token is absent or invalid. This
     *     deliberately never throws — an invalid token is an authentication outcome, not an error.
     */
    public Optional<AuthenticatedUser> parse(String token) {
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
                log.debug("Rejected token: audience {} does not contain {}",
                        claims.getAudience(), properties.getAudience());
                return Optional.empty();
            }
            return Optional.of(toPrincipal(claims));
        } catch (ExpiredJwtException ex) {
            log.debug("Rejected token: expired at {}", ex.getClaims().getExpiration());
            return Optional.empty();
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected token: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean audienceMatches(Claims claims) {
        Object audience = claims.get("aud");
        if (audience == null) {
            return false;
        }
        if (audience instanceof Collection<?> values) {
            return values.stream().anyMatch(value -> properties.getAudience().equals(String.valueOf(value)));
        }
        return properties.getAudience().equals(String.valueOf(audience));
    }

    private AuthenticatedUser toPrincipal(Claims claims) {
        Long userId = Long.valueOf(claims.getSubject());
        String name = claims.get(CLAIM_NAME, String.class);
        String email = claims.get(CLAIM_EMAIL, String.class);

        List<RoleName> roles = new ArrayList<>();
        Object raw = claims.get(CLAIM_ROLES);
        if (raw instanceof Collection<?> values) {
            for (Object value : values) {
                try {
                    roles.add(RoleName.from(String.valueOf(value)));
                } catch (IllegalArgumentException ex) {
                    log.warn("Ignoring unrecognised role '{}' in token for user {}", value, userId);
                }
            }
        }
        return new AuthenticatedUser(userId, name, email, roles);
    }

    /** Extracts the raw token from an {@code Authorization: Bearer <token>} header value. */
    public static Optional<String> extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Optional.empty();
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
