package com.earthscan.land.dto;

import com.earthscan.land.domain.SoilType;
import io.swagger.v3.oas.annotations.media.Schema;

/** Reference data for the listing form's soil dropdown. */
@Schema(name = "SoilTypeResponse")
public record SoilTypeResponse(
        Integer id,
        String name,
        int fertilityIndex,
        int waterRetentionIndex,
        String description) {

    public static SoilTypeResponse from(SoilType soilType) {
        return new SoilTypeResponse(
                soilType.getId(),
                soilType.getName(),
                soilType.getFertilityIndex(),
                soilType.getWaterRetentionIndex(),
                soilType.getDescription());
    }
}
