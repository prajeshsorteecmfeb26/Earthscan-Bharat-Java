package com.earthscan.land.service.scoring;

import org.springframework.stereotype.Component;

/** Long-term annual rainfall, saturating at both ends of the band. */
@Component
public class RainfallFactor implements ScoringFactor {

    /** At or above this, the factor saturates: more rain stops adding agricultural value. */
    static final double AMPLE_MM = 1200.0;

    /** At or below this, rain-fed cultivation is not viable without irrigation. */
    static final double SCARCE_MM = 400.0;

    @Override
    public String name() {
        return "rainfall";
    }

    @Override
    public String description() {
        return "Long-term average annual rainfall";
    }

    @Override
    public double weight() {
        return 0.15;
    }

    @Override
    public double score(ScoringContext context) {
        Integer rainfall = context.annualRainfallMm();
        if (rainfall == null) {
            return 50.0;
        }
        return ScoringMath.ascendingScore(rainfall, SCARCE_MM, AMPLE_MM);
    }
}
