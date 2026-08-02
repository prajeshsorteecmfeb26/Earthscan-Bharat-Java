package com.earthscan.common.security;

import java.util.Arrays;

/**
 * The four roles supported by the EarthScan Bharat platform.
 *
 * <p>The enum constant is the canonical, storage-and-token form ({@code LAND_BUYER}); the
 * {@code displayName} is the human readable form the React client already sends and renders
 * ({@code "Land Buyer"}). Keeping both lets this Spring Boot backend be a drop-in replacement for
 * the previous ASP.NET Core API without rewriting the existing frontend contract.</p>
 */
public enum RoleName {

    FARMER("Farmer"),
    LAND_BUYER("Land Buyer"),
    AGRICULTURE_EXPERT("Agriculture Expert"),
    ADMIN("Admin");

    public static final String AUTHORITY_PREFIX = "ROLE_";

    private final String displayName;

    RoleName(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Spring Security authority, e.g. {@code ROLE_LAND_BUYER}. */
    public String getAuthority() {
        return AUTHORITY_PREFIX + name();
    }

    /**
     * Resolves a role from either its canonical name or its display name, case-insensitively and
     * tolerating spaces, hyphens and underscores.
     *
     * @throws IllegalArgumentException when the value matches no known role
     */
    public static RoleName from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Role must not be blank");
        }
        String normalised = normalise(value);
        return Arrays.stream(values())
                .filter(role -> normalise(role.name()).equals(normalised)
                        || normalise(role.displayName).equals(normalised))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + value));
    }

    private static String normalise(String value) {
        return value.trim().toUpperCase().replace(' ', '_').replace('-', '_');
    }
}
