package com.earthscan.land.service.scoring;

import org.springframework.stereotype.Component;

/**
 * Practical usability: existing irrigation infrastructure and road access.
 *
 * <p>The lightest factor at 10%. Both are improvable by the buyer with capital, unlike soil and
 * hydrology, so they should shift a ranking rather than dominate it.</p>
 */
@Component
public class AccessibilityFactor implements ScoringFactor {

    static final double IRRIGATION_POINTS = 60.0;
    static final double ROAD_POINTS = 40.0;

    @Override
    public String name() {
        return "accessibility";
    }

    @Override
    public String description() {
        return "Existing irrigation infrastructure and road access";
    }

    @Override
    public double weight() {
        return 0.10;
    }

    @Override
    public double score(ScoringContext context) {
        double score = 0.0;
        if (context.irrigationAvailable()) {
            score += IRRIGATION_POINTS;
        }
        if (context.roadAccess()) {
            score += ROAD_POINTS;
        }
        return score;
    }
}
