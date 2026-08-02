package com.earthscan.land.dto;

import com.earthscan.land.domain.SavedSearch;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "SavedSearchResponse")
public record SavedSearchResponse(
        Long id,
        String label,
        String locationQuery,
        String soilTypeName,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Double minScore,
        Long landId,
        boolean notifyOnMatch,
        Instant createdAt) {

    public static SavedSearchResponse from(SavedSearch entity) {
        return new SavedSearchResponse(
                entity.getId(),
                entity.getLabel(),
                entity.getLocationQuery(),
                entity.getSoilTypeName(),
                entity.getMinPrice(),
                entity.getMaxPrice(),
                entity.getMinScore(),
                entity.getLandId(),
                entity.isNotifyOnMatch(),
                entity.getCreatedAt());
    }
}
