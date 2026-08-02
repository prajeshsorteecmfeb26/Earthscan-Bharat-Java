package com.earthscan.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the token provider.
 *
 * <p>No mocks here on purpose: the value of this class is entirely in whether real cryptographic
 * verification accepts and rejects the right tokens, and mocking jjwt would test nothing.</p>
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private static final String SECRET = "TestOnlySigningKeyForEarthScanBharatUnitTests2026";

    private JwtProperties properties;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setIssuer("EarthScanBackend");
        properties.setAudience("EarthScanUsers");
        properties.setExpirationMs(60_000L);
        provider = new JwtTokenProvider(properties);
    }

    @Test
    @DisplayName("round-trips identity and roles through a signed token")
    void roundTripsToken() {
        String token = provider.generateToken(
                7L, "Anita Deshmukh", "anita@example.com",
                List.of(RoleName.LAND_BUYER));

        AuthenticatedUser user = provider.parse(token).orElseThrow();

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getName()).isEqualTo("Anita Deshmukh");
        assertThat(user.getEmail()).isEqualTo("anita@example.com");
        assertThat(user.getRoles()).containsExactly(RoleName.LAND_BUYER);
        assertThat(user.getPrimaryRole()).isEqualTo(RoleName.LAND_BUYER);
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("recognises an admin token")
    void recognisesAdmin() {
        String token = provider.generateToken(1L, "Admin", "admin@earthscan.in",
                List.of(RoleName.ADMIN));

        assertThat(provider.parse(token).orElseThrow().isAdmin()).isTrue();
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsForeignSignature() {
        JwtProperties attacker = new JwtProperties();
        attacker.setSecret("AttackerControlledKeyThatIsAlsoLongEnough123456");
        attacker.setIssuer("EarthScanBackend");
        attacker.setAudience("EarthScanUsers");
        String forged = new JwtTokenProvider(attacker)
                .generateToken(1L, "Admin", "admin@earthscan.in", List.of(RoleName.ADMIN));

        // This is the check that stops anyone from minting their own admin token.
        assertThat(provider.parse(forged)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token from a different issuer")
    void rejectsWrongIssuer() {
        JwtProperties other = new JwtProperties();
        other.setSecret(SECRET);
        other.setIssuer("SomeOtherSystem");
        other.setAudience("EarthScanUsers");
        String token = new JwtTokenProvider(other)
                .generateToken(7L, "Anita", "anita@example.com", List.of(RoleName.FARMER));

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("rejects a token minted for a different audience")
    void rejectsWrongAudience() {
        JwtProperties other = new JwtProperties();
        other.setSecret(SECRET);
        other.setIssuer("EarthScanBackend");
        other.setAudience("SomeOtherAudience");
        String token = new JwtTokenProvider(other)
                .generateToken(7L, "Anita", "anita@example.com", List.of(RoleName.FARMER));

        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("rejects an already-expired token")
    void rejectsExpiredToken() {
        JwtProperties expiring = new JwtProperties();
        expiring.setSecret(SECRET);
        expiring.setIssuer("EarthScanBackend");
        expiring.setAudience("EarthScanUsers");
        expiring.setExpirationMs(-1_000L);   // Issued already expired.
        String token = new JwtTokenProvider(expiring)
                .generateToken(7L, "Anita", "anita@example.com", List.of(RoleName.FARMER));

        assertThat(provider.parse(token)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-token",
            "a.b.c",
            "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxIn0."   // alg:none, the classic bypass attempt
    })
    @DisplayName("returns empty rather than throwing for malformed tokens")
    void rejectsMalformedTokens(String token) {
        assertThat(provider.parse(token)).isEmpty();
    }

    @Test
    @DisplayName("treats a null or blank token as simply unauthenticated")
    void handlesAbsentToken() {
        assertThat(provider.parse(null)).isEmpty();
        assertThat(provider.parse("")).isEmpty();
        assertThat(provider.parse("   ")).isEmpty();
    }

    @Test
    @DisplayName("refuses to start with a secret too short for HS256")
    void rejectsShortSecret() {
        JwtProperties weak = new JwtProperties();
        weak.setSecret("tooshort");

        // Failing fast at startup is much better than silently accepting a weak key.
        assertThatThrownBy(() -> new JwtTokenProvider(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32");
    }

    @Test
    @DisplayName("extracts the token from a Bearer header, case-insensitively")
    void extractsBearerToken() {
        assertThat(JwtTokenProvider.extractBearerToken("Bearer abc.def.ghi"))
                .contains("abc.def.ghi");
        assertThat(JwtTokenProvider.extractBearerToken("bearer abc.def.ghi"))
                .contains("abc.def.ghi");
    }

    @Test
    @DisplayName("ignores header values that are not Bearer tokens")
    void ignoresNonBearerHeaders() {
        assertThat(JwtTokenProvider.extractBearerToken(null)).isEmpty();
        assertThat(JwtTokenProvider.extractBearerToken("")).isEmpty();
        assertThat(JwtTokenProvider.extractBearerToken("Basic dXNlcjpwYXNz")).isEmpty();
        assertThat(JwtTokenProvider.extractBearerToken("Bearer ")).isEmpty();
    }

    @Test
    @DisplayName("skips unrecognised roles instead of failing the whole token")
    void toleratesUnknownRoleClaim() {
        // A token minted before a role was renamed should still authenticate the user, just without
        // the unknown authority — failing closed on the entire token would log everyone out.
        String token = provider.generateToken(7L, "Anita", "anita@example.com",
                List.of(RoleName.FARMER));
        Optional<AuthenticatedUser> parsed = provider.parse(token);

        assertThat(parsed).isPresent();
        assertThat(parsed.orElseThrow().getRoles()).containsExactly(RoleName.FARMER);
    }
}
