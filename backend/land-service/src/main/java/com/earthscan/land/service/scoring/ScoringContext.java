package com.earthscan.land.service.scoring;

import com.earthscan.land.domain.Land;
import lombok.Builder;

/**
 * The inputs a scoring factor is allowed to see.
 *
 * <p>Exists so factors depend on an abstraction rather than on the {@link Land} entity directly.
 * That matters for three reasons: a factor becomes testable without constructing a JPA entity, a
 * factor cannot accidentally reach for {@code ownerId} or {@code price} and make the score depend on
 * who is selling, and the entity can be refactored without touching five factor implementations.</p>
 *
 * <p>Nullable fields are modelled as boxed types on purpose — "rainfall unknown" and "rainfall zero"
 * are different facts and each factor decides how to treat the difference.</p>
 */
@Builder
public record ScoringContext(

        /** 0-100, from the soil_types reference table. */
        int soilFertilityIndex,

        /** 0-100, from the soil_types reference table. */
        int soilWaterRetentionIndex,

        /** Metres below ground level. Null when unsurveyed. */
        Double groundwaterDepthMetres,

        /** Long-term annual average in mm. Null when unknown. */
        Integer annualRainfallMm,

        boolean irrigationAvailable,

        boolean roadAccess) {

    /** Adapts a persisted listing into a scoring context. */
    public static ScoringContext from(Land land) {
        return ScoringContext.builder()
                .soilFertilityIndex(land.getSoilType() == null ? 0 : land.getSoilType().getFertilityIndex())
                .soilWaterRetentionIndex(
                        land.getSoilType() == null ? 0 : land.getSoilType().getWaterRetentionIndex())
                .groundwaterDepthMetres(land.getGroundwaterLevelDepth())
                .annualRainfallMm(land.getAnnualRainfallMm())
                .irrigationAvailable(land.isIrrigationAvailable())
                .roadAccess(land.isRoadAccess())
                .build();
    }
}
