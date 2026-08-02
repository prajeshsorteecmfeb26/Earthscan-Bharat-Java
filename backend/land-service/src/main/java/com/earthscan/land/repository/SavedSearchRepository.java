package com.earthscan.land.repository;

import com.earthscan.land.domain.SavedSearch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndLandId(Long userId, Long landId);

    /** Used by the {@code user.deleted} consumer to purge a removed user's shortlist. */
    long deleteByUserId(Long userId);

    /** Candidate searches to match a newly listed property against. */
    List<SavedSearch> findByNotifyOnMatchTrue();
}
