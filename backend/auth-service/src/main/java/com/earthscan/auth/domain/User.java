package com.earthscan.auth.domain;

import com.earthscan.common.security.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A platform account.
 *
 * <p>Note {@code passwordHash}, never {@code password}: the plaintext is accepted by the DTO,
 * hashed in the service layer, and never reaches this class or the database.</p>
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        indexes = @Index(name = "idx_users_email", columnList = "email"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /**
     * EAGER on purpose. Roles are needed on literally every read of a user — to build the JWT, to
     * render the admin table, to authorise a request — so lazy loading here would only trade a join
     * for a guaranteed second query plus the risk of a
     * {@code LazyInitializationException} once the entity leaves the transaction.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_user_roles_user")),
            inverseJoinColumns = @JoinColumn(name = "role_id", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_user_roles_role")))
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
        // Required by JPA.
    }

    public User(String name, String email, String passwordHash) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Replaces the user's roles. The platform gives each account exactly one. */
    public void assignRole(Role role) {
        this.roles.clear();
        this.roles.add(role);
    }

    public List<RoleName> getRoleNames() {
        return roles.stream().map(Role::getName).toList();
    }

    /** The role the UI displays, e.g. {@code "Land Buyer"}. */
    public String getPrimaryRoleDisplayName() {
        return roles.stream()
                .findFirst()
                .map(Role::getDisplayName)
                .orElse(RoleName.FARMER.getDisplayName());
    }

    public RoleName getPrimaryRoleName() {
        return roles.stream().findFirst().map(Role::getName).orElse(RoleName.FARMER);
    }

    public boolean hasRole(RoleName roleName) {
        return roles.stream().anyMatch(role -> role.getName() == roleName);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof User user)) {
            return false;
        }
        return id != null && Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        // Deliberately omits passwordHash so an accidental log statement cannot leak it.
        return "User{id=" + id + ", email='" + email + "', roles=" + getRoleNames() + "}";
    }
}
