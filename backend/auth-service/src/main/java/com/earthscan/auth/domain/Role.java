package com.earthscan.auth.domain;

import com.earthscan.common.security.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * A role, stored in its own table rather than as a string column on {@code users}.
 *
 * <p>The ASP.NET version kept {@code Role} as a {@code VARCHAR(20)} on the user row. That is a 2NF
 * violation waiting to happen — the role's display label and description are facts about the role,
 * not about the user, so they would have to be duplicated on every row that shares that role, and a
 * typo in one row silently creates a fifth role. Normalising to {@code roles} + {@code user_roles}
 * makes the set of legal roles a property of the schema.</p>
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 30)
    private RoleName name;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "description", length = 255)
    private String description;

    protected Role() {
        // Required by JPA.
    }

    public Role(RoleName name, String description) {
        this.name = name;
        this.displayName = name.getDisplayName();
        this.description = description;
    }

    public Integer getId() {
        return id;
    }

    public RoleName getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Role role)) {
            return false;
        }
        return name == role.name;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "Role{" + name + "}";
    }
}
