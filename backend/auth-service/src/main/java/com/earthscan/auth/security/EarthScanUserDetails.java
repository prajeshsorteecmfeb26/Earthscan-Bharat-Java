package com.earthscan.auth.security;

import com.earthscan.auth.domain.User;
import com.earthscan.common.security.RoleName;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapts the {@link User} entity to Spring Security's {@link UserDetails} contract.
 *
 * <p>A dedicated adapter rather than making {@code User} implement {@code UserDetails} directly. The
 * entity is a persistence concern and the security contract is a framework concern; conflating them
 * drags {@code getUsername()} and {@code isAccountNonExpired()} into the JPA model, where Hibernate
 * will try to interpret them as properties, and couples the schema to a Spring interface.</p>
 */
@Getter
public class EarthScanUserDetails implements UserDetails {

    private final Long id;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final boolean enabled;
    private final List<RoleName> roles;
    private final List<GrantedAuthority> authorities;

    public EarthScanUserDetails(User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.roles = user.getRoleNames();
        this.authorities = user.getRoleNames().stream()
                .map(RoleName::getAuthority)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Email is the login identifier on this platform; there is no separate username. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        // The platform has no account-expiry concept. Returning the enabled flag instead would
        // conflate two distinct states and produce a misleading exception on a disabled login.
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        // No password-rotation policy yet. See the security notes in the README.
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        // Never include passwordHash - this object reaches log statements and debuggers.
        return "EarthScanUserDetails{id=" + id + ", email='" + email + "', roles=" + roles + "}";
    }
}
