package com.earthscan.land.service.scoring;

import org.springframework.stereotype.Component;

/**
 * Soil productivity, read straight from the {@code soil_types} reference table.
 *
 * <p>The index is data rather than code specifically so an agronomist can recalibrate a soil with an
 * {@code UPDATE} instead of a redeploy. This factor therefore does no interpretation of its own — it
 * only clamps, in case a bad row is entered.</p>
 */
@Component
public class SoilFertilityFactor implements ScoringFactor {

    @Override
    public String name() {
        return "soilFertility";
    }

    @Override
    public String description() {
        return "Productivity of the soil class for typical Maharashtra cropping patterns";
    }

    @Override
    public double weight() {
        return 0.30;
    }

    @Override
    public double score(ScoringContext context) {
        return ScoringMath.clamp(context.soilFertilityIndex(), 0, 100);
    }
}
