package com.earthscan.auth.repository;

import com.earthscan.auth.domain.User;
import com.earthscan.common.security.RoleName;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for {@link User}. */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /** Lookup used on every login. Backed by the unique index on {@code email}. */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Free-text search over name and email for the admin console.
     *
     * <p>Written as an explicit query rather than a derived method name because the derived
     * equivalent ({@code findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase}) needs the term
     * passed twice, which is easy to get wrong at the call site.</p>
     */
    @Query("""
            SELECT u FROM User u
            WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :term, '%'))
            """)
    Page<User> search(@Param("term") String term, Pageable pageable);

    long countByRoles_Name(RoleName roleName);
}
