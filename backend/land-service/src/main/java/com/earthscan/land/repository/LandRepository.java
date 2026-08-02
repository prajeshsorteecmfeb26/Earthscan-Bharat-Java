package com.earthscan.land.repository;

import com.earthscan.land.domain.Land;
import com.earthscan.land.domain.ListingStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Land}.
 *
 * <p>Extends {@link JpaSpecificationExecutor} because the search screen combines up to six optional
 * filters. Derived query methods cannot express "apply this predicate only when the parameter is
 * present" — you would need a separate method for each of the 64 combinations, or a
 * {@code @Query} full of {@code (:param IS NULL OR ...)} clauses that defeat index selection.
 * Specifications build only the predicates that are actually supplied.</p>
 */
@Repository
public interface LandRepository extends JpaRepository<Land, Long>, JpaSpecificationExecutor<Land> {

    List<Land> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    long countByOwnerId(Long ownerId);

    long countByStatus(ListingStatus status);

    /** Used by the {@code user.deleted} consumer. */
    long deleteByOwnerId(Long ownerId);

    /** Distinct districts, for populating the search screen's filter dropdown. */
    @Query("SELECT DISTINCT l.district FROM Land l WHERE l.district IS NOT NULL ORDER BY l.district")
    List<String> findDistinctDistricts();

    /**
     * Comparable listings for the investment analysis page: same district, similar size, excluding
     * the subject property itself.
     */
    @Query("""
            SELECT l FROM Land l
            WHERE l.district = :district
              AND l.id <> :excludeId
              AND l.status = com.earthscan.land.domain.ListingStatus.ACTIVE
              AND l.sizeInAcres BETWEEN :minSize AND :maxSize
            ORDER BY l.landIntelligenceScore DESC
            """)
    List<Land> findComparables(@Param("district") String district,
                               @Param("excludeId") Long excludeId,
                               @Param("minSize") Double minSize,
                               @Param("maxSize") Double maxSize);

    /** Average price per acre in a district, the anchor for the "fair price" verdict. */
    @Query("""
            SELECT AVG(l.price / l.sizeInAcres) FROM Land l
            WHERE l.district = :district
              AND l.sizeInAcres > 0
              AND l.status = com.earthscan.land.domain.ListingStatus.ACTIVE
            """)
    Double findAveragePricePerAcreByDistrict(@Param("district") String district);
}
