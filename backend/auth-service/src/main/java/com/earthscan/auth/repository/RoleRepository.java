package com.earthscan.auth.repository;

import com.earthscan.auth.domain.Role;
import com.earthscan.common.security.RoleName;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the {@code roles} lookup table. */
@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(RoleName name);

    boolean existsByName(RoleName name);
}
