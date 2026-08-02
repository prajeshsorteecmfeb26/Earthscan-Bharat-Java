package com.earthscan.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.earthscan.auth.domain.Role;
import com.earthscan.auth.domain.User;
import com.earthscan.common.security.RoleName;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Repository slice tests against a real database engine (H2 in MySQL mode).
 *
 * <p>These test what Mockito cannot. A mocked repository returns whatever the test tells it to, so it
 * proves nothing about whether the derived query name actually parses, whether the unique constraint
 * exists, or whether the {@code countByRoles_Name} property path resolves across the join table.
 * Every assertion here is about the mapping being right rather than about business logic.</p>
 *
 * <p>{@code @DataJpaTest} rolls each test back, so ordering between methods cannot leak state.</p>
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository (JPA slice)")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private EntityManager entityManager;

    private Role farmerRole;
    private Role adminRole;

    @BeforeEach
    void seedRoles() {
        farmerRole = roleRepository.save(new Role(RoleName.FARMER, "cultivates land"));
        adminRole = roleRepository.save(new Role(RoleName.ADMIN, "administers the platform"));
    }

    private User persistUser(String name, String email, Role role) {
        User user = new User(name, email, "$2a$10$notarealhash");
        user.assignRole(role);
        return userRepository.saveAndFlush(user);
    }

    @Test
    @DisplayName("finds a user by email regardless of the case supplied")
    void findsByEmailIgnoringCase() {
        persistUser("Anita Deshmukh", "anita@example.com", farmerRole);

        assertThat(userRepository.findByEmailIgnoreCase("ANITA@EXAMPLE.COM")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("Anita@Example.Com")).isPresent();
        assertThat(userRepository.findByEmailIgnoreCase("anita@example.com")).isPresent();
    }

    @Test
    @DisplayName("returns empty rather than throwing for an unknown email")
    void returnsEmptyForUnknownEmail() {
        assertThat(userRepository.findByEmailIgnoreCase("nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("the unique constraint on email is actually present in the schema")
    void enforcesUniqueEmail() {
        persistUser("Anita Deshmukh", "anita@example.com", farmerRole);

        // The service layer checks for duplicates before inserting, but that check is a race under
        // concurrency. This asserts the database itself is the final guarantee.
        assertThatThrownBy(() -> persistUser("Someone Else", "anita@example.com", farmerRole))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByEmailIgnoreCase agrees with findByEmailIgnoreCase")
    void existsMatchesFind() {
        persistUser("Anita Deshmukh", "anita@example.com", farmerRole);

        assertThat(userRepository.existsByEmailIgnoreCase("ANITA@example.com")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("nobody@example.com")).isFalse();
    }

    @Test
    @DisplayName("counts users by role across the user_roles join table")
    void countsByRole() {
        persistUser("Farmer One", "f1@example.com", farmerRole);
        persistUser("Farmer Two", "f2@example.com", farmerRole);
        persistUser("The Admin", "admin@example.com", adminRole);

        // This is the property path (roles.name) that the last-admin guard depends on. If it stopped
        // resolving, the guard would silently always pass and an admin could delete the last admin.
        assertThat(userRepository.countByRoles_Name(RoleName.FARMER)).isEqualTo(2);
        assertThat(userRepository.countByRoles_Name(RoleName.ADMIN)).isEqualTo(1);
        assertThat(userRepository.countByRoles_Name(RoleName.LAND_BUYER)).isZero();
    }

    @Test
    @DisplayName("search matches on either name or email, case-insensitively")
    void searchesNameAndEmail() {
        persistUser("Anita Deshmukh", "anita@example.com", farmerRole);
        persistUser("Ravi Patil", "ravi.patil@example.com", farmerRole);
        persistUser("Sunita Rao", "sunita@other.org", adminRole);

        Page<User> byName = userRepository.search("deshmukh", PageRequest.of(0, 10));
        assertThat(byName.getContent()).extracting(User::getEmail)
                .containsExactly("anita@example.com");

        Page<User> byEmailDomain = userRepository.search("example.com", PageRequest.of(0, 10));
        assertThat(byEmailDomain.getTotalElements()).isEqualTo(2);

        // "nita" appears inside both "Anita" and "Sunita" — substring matching, not prefix.
        Page<User> substring = userRepository.search("nita", PageRequest.of(0, 10));
        assertThat(substring.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("search returns nothing for a term that matches no row")
    void searchReturnsEmptyPage() {
        persistUser("Anita Deshmukh", "anita@example.com", farmerRole);

        assertThat(userRepository.search("zzzznomatch", PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    @DisplayName("search paginates and sorts")
    void searchPaginatesAndSorts() {
        for (int i = 1; i <= 5; i++) {
            persistUser("User " + i, "user" + i + "@example.com", farmerRole);
        }

        Page<User> firstPage = userRepository.search("example.com",
                PageRequest.of(0, 2, Sort.by(Sort.Direction.ASC, "email")));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent().get(0).getEmail()).isEqualTo("user1@example.com");
    }

    @Test
    @DisplayName("roles are fetched eagerly, so a detached user can still report its role")
    void rolesSurviveDetachment() {
        User saved = persistUser("Anita Deshmukh", "anita@example.com", farmerRole);
        Long id = saved.getId();

        // Detaching mimics the entity leaving the transaction, which is where a LAZY association
        // would blow up with LazyInitializationException.
        entityManager.clear();

        Optional<User> reloaded = userRepository.findById(id);
        assertThat(reloaded).isPresent();
        entityManager.detach(reloaded.orElseThrow());

        assertThat(reloaded.orElseThrow().getPrimaryRoleDisplayName()).isEqualTo("Farmer");
    }

    @Test
    @DisplayName("assigning a role replaces rather than accumulates")
    void assignRoleReplaces() {
        User user = persistUser("Ravi Patil", "ravi@example.com", farmerRole);

        user.assignRole(adminRole);
        userRepository.saveAndFlush(user);
        entityManager.clear();

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        // The platform gives each account exactly one role; accumulating would silently grant
        // someone both Farmer and Admin authorities.
        assertThat(reloaded.getRoles()).hasSize(1);
        assertThat(reloaded.getPrimaryRoleName()).isEqualTo(RoleName.ADMIN);
    }

    @Test
    @DisplayName("createdAt is populated and updatedAt moves on modification")
    void timestampsBehave() {
        User user = persistUser("Anita Deshmukh", "anita@example.com", farmerRole);
        assertThat(user.getCreatedAt()).isNotNull();

        user.setName("Anita D.");
        User updated = userRepository.saveAndFlush(user);

        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    }

    @Test
    @DisplayName("the roles lookup table enforces uniqueness on role name")
    void rolesTableEnforcesUniqueness() {
        assertThatThrownBy(() -> roleRepository.saveAndFlush(new Role(RoleName.FARMER, "duplicate")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
