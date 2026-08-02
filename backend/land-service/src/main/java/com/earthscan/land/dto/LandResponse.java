package com.earthscan.land.dto;

import com.earthscan.land.domain.Land;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;

/** Listing projection returned to clients. */
@Schema(name = "LandResponse")
public record LandResponse(
        Long id,
        String title,
        String description,
        String location,
        String district,
        String state,
        Double latitude,
        Double longitude,
        BigDecimal price,
        BigDecimal pricePerAcre,
        Double sizeInAcres,
        String soilType,
        Double groundwaterLevelDepth,
        Integer annualRainfallMm,
        boolean irrigationAvailable,
        boolean roadAccess,
        @Schema(description = "0-100 composite agricultural suitability score")
        Double landIntelligenceScore,
        @Schema(description = "0-100 estimated probability that a borewell yields usable water")
        Double borewellSuccessProbability,
        Long ownerId,
        String status,
        boolean verified,
        Instant createdAt) {

    public static LandResponse from(Land land) {
        return new LandResponse(
                land.getId(),
                land.getTitle(),
                land.getDescription(),
                land.getLocation(),
                land.getDistrict(),
                land.getState(),
                land.getLatitude(),
                land.getLongitude(),
                land.getPrice(),
                land.getPricePerAcre(),
                land.getSizeInAcres(),
                land.getSoilType() == null ? null : land.getSoilType().getName(),
                land.getGroundwaterLevelDepth(),
                land.getAnnualRainfallMm(),
                land.isIrrigationAvailable(),
                land.isRoadAccess(),
                land.getLandIntelligenceScore(),
                land.getBorewellSuccessProbability(),
                land.getOwnerId(),
                land.getStatus().name(),
                land.isVerified(),
                land.getCreatedAt());
    }
}
