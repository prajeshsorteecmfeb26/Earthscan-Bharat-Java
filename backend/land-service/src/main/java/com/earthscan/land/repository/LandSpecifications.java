package com.earthscan.land.repository;

import com.earthscan.land.domain.Land;
import com.earthscan.land.domain.ListingStatus;
import com.earthscan.land.dto.LandSearchCriteria;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Translates {@link LandSearchCriteria} into a JPA {@link Specification}.
 *
 * <p>Only the predicates for supplied filters are added, so an unfiltered search produces
 * {@code WHERE status = 'ACTIVE'} and can use the status index, rather than a chain of
 * {@code (:param IS NULL OR column = :param)} clauses that force a full scan.</p>
 */
public final class LandSpecifications {

    private LandSpecifications() {
    }

    public static Specification<Land> matching(LandSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Sold, draft and withdrawn listings never appear in search results.
            predicates.add(builder.equal(root.get("status"), ListingStatus.ACTIVE));

            if (hasText(criteria.q())) {
                String pattern = "%" + criteria.q().trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern),
                        builder.like(builder.lower(root.get("location")), pattern)));
            }

            if (hasText(criteria.district())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("district")), criteria.district().trim().toLowerCase()));
            }

            if (hasText(criteria.soilType())) {
                predicates.add(builder.equal(
                        builder.lower(root.get("soilType").get("name")),
                        criteria.soilType().trim().toLowerCase()));
            }

            if (criteria.minPrice() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
            }
            if (criteria.maxPrice() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
            }

            if (criteria.minSize() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("sizeInAcres"), criteria.minSize()));
            }
            if (criteria.maxSize() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("sizeInAcres"), criteria.maxSize()));
            }

            if (criteria.minScore() != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("landIntelligenceScore"), criteria.minScore()));
            }

            if (criteria.irrigationAvailable() != null) {
                predicates.add(builder.equal(
                        root.get("irrigationAvailable"), criteria.irrigationAvailable()));
            }

            if (Boolean.TRUE.equals(criteria.verifiedOnly())) {
                predicates.add(builder.isTrue(root.get("verified")));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
