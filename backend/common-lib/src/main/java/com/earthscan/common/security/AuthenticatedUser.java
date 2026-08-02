package com.earthscan.common.security;

import java.util.List;
import java.util.Objects;

/**
 * Immutable principal reconstructed from the JWT. Downstream services never call auth-service to
 * find out who is calling — the token itself carries the identity.
 */
public final class AuthenticatedUser {

    private final Long id;
    private final String name;
    private final String email;
    private final List<RoleName> roles;

    public AuthenticatedUser(Long id, String name, String email, List<RoleName> roles) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public List<RoleName> getRoles() {
        return roles;
    }

    /** The role shown in the UI. A user has exactly one role in practice; this returns the first. */
    public RoleName getPrimaryRole() {
        return roles.isEmpty() ? RoleName.FARMER : roles.get(0);
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole(RoleName.ADMIN);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthenticatedUser that)) {
            return false;
        }
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + ", email='" + email + "'}";
    }
}
