package com.earthscan.land.service.scoring;

import org.springframework.stereotype.Component;

/**
 * How easily water can be reached, by depth to the water table.
 *
 * <p>Tied for the heaviest weight with soil fertility, because in this region water access is what
 * most often decides whether a parcel can be farmed profitably at all.</p>
 */
@Component
public class WaterAccessFactor implements ScoringFactor {

    /** At or above this depth, water is shallow enough to count as freely available. */
    static final double SHALLOW_DEPTH_M = 30.0;

    /** Beyond this depth, extraction is expensive and yield is usually unreliable. */
    static final double DEEP_DEPTH_M = 250.0;

    @Override
    public String name() {
        return "waterAccess";
    }

    @Override
    public String description() {
        return "Depth to the water table; shallower is better";
    }

    @Override
    public double weight() {
        return 0.30;
    }

    @Override
    public double score(ScoringContext context) {
        Double depth = context.groundwaterDepthMetres();
        if (depth == null) {
            // Neutral, not zero. An unsurveyed parcel should not be penalised as though it were
            // known to be dry - that would rank "no data" below "confirmed bad", which is wrong.
            return 50.0;
        }
        return ScoringMath.descendingScore(depth, SHALLOW_DEPTH_M, DEEP_DEPTH_M);
    }
}
