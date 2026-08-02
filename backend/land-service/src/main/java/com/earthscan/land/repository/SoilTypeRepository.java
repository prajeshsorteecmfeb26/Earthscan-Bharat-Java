package com.earthscan.land.repository;

import com.earthscan.land.domain.SoilType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoilTypeRepository extends JpaRepository<SoilType, Integer> {

    Optional<SoilType> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);
}
