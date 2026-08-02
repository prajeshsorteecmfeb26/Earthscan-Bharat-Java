package com.earthscan.land.service.scoring;

import org.springframework.stereotype.Component;

/**
 * How well the soil holds moisture between irrigation cycles.
 *
 * <p>Separate from soil fertility on purpose. The two are correlated but not the same: heavy clay
 * retains water very well while being only moderately fertile, and a light sandy loam can be
 * workable yet drain too fast to carry a crop through a dry spell.</p>
 */
@Component
public class MoistureRetentionFactor implements ScoringFactor {

    @Override
    public String name() {
        return "moistureRetention";
    }

    @Override
    public String description() {
        return "Ability of the soil to hold moisture between irrigation cycles";
    }

    @Override
    public double weight() {
        return 0.15;
    }

    @Override
    public double score(ScoringContext context) {
        return ScoringMath.clamp(context.soilWaterRetentionIndex(), 0, 100);
    }
}
