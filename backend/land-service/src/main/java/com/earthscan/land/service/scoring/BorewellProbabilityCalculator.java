package com.earthscan.land.service.scoring;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Estimates the probability that a borewell on a parcel yields usable water.
 *
 * <p>Extracted from {@code LandScoringService} for Single Responsibility. It shares inputs with the
 * Land Intelligence Score but is a different calculation answering a different question, on a
 * different scale, with different bounds — and, importantly, with different consequences when wrong.
 * A suitability score that is 10 points optimistic costs a buyer a poor ranking; a borewell
 * probability that is 10 points optimistic costs a farmer the price of a dry bore.</p>
 *
 * <p>Deliberately <em>not</em> a {@link ScoringFactor}: it is not a weighted component of the
 * composite score, and making it one would have it silently absorbed into a number it should stand
 * apart from.</p>
 */
@Slf4j
@Component
public class BorewellProbabilityCalculator {

    /** Never returns certainty. No heuristic can promise water to someone about to pay to drill. */
    static final double CEILING = 95.0;

    /** Never returns zero. Deep drilling does occasionally succeed. */
    static final double FLOOR = 10.0;

    /** Recharge modifiers are capped at roughly ±3 points so depth stays dominant. */
    private static final double MODIFIER_SCALE = 0.06;
    private static final double IRRIGATION_BONUS = 3.0;

    private final RainfallFactor rainfallFactor;

    public BorewellProbabilityCalculator(RainfallFactor rainfallFactor) {
        // Reuses the rainfall normalisation rather than duplicating the band constants, so a
        // recalibration of the rainfall thresholds cannot leave the two calculations disagreeing.
        this.rainfallFactor = rainfallFactor;
    }

    /**
     * @return probability in {@code [10, 95]} that a borewell here yields usable water
     */
    public double calculate(ScoringContext context) {
        double depthComponent = depthComponent(context.groundwaterDepthMetres());

        // Small adjustments for aquifer recharge. Centred on 50 so an average soil or an average
        // rainfall contributes nothing either way.
        double retentionModifier =
                (ScoringMath.clamp(context.soilWaterRetentionIndex(), 0, 100) - 50) * MODIFIER_SCALE;
        double rainfallModifier = (rainfallFactor.score(context) - 50) * MODIFIER_SCALE;
        double irrigationModifier = context.irrigationAvailable() ? IRRIGATION_BONUS : 0.0;

        double result = ScoringMath.clamp(
                depthComponent + retentionModifier + rainfallModifier + irrigationModifier,
                FLOOR, CEILING);

        log.trace("Borewell probability {} from depth component {} plus modifiers ({}, {}, {})",
                result, depthComponent, retentionModifier, rainfallModifier, irrigationModifier);
        return result;
    }

    /**
     * Depth-driven base estimate, decaying linearly across the viable band.
     *
     * <p>Continuous by design. The implementation this replaced returned exactly 90%, 65% or 30% from
     * three buckets, which meant 49.9 m returned 90% and 50.1 m returned 65% — a 25-point cliff over
     * ten centimetres of depth between two neighbouring plots.</p>
     */
    private double depthComponent(Double depthMetres) {
        if (depthMetres == null) {
            // Pessimistic when depth is unknown. Encouraging someone to drill on the strength of
            // absent data would be the wrong default, which is why this differs from the neutral
            // treatment WaterAccessFactor gives the same missing value.
            return FLOOR + (CEILING - FLOOR) * 0.15;
        }
        double normalised = ScoringMath.descendingScore(
                depthMetres, WaterAccessFactor.SHALLOW_DEPTH_M, WaterAccessFactor.DEEP_DEPTH_M);
        return FLOOR + (CEILING - FLOOR) * (normalised / 100.0);
    }
}
