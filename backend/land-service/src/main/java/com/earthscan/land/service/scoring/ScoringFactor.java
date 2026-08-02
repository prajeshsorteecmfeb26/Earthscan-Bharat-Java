package com.earthscan.land.service.scoring;

/**
 * One weighted component of the Land Intelligence Score. <strong>Strategy pattern.</strong>
 *
 * <p>Each factor is an independent Spring bean, and {@code LandScoringService} composes whichever
 * ones are on the classpath. The point is Open/Closed: adding a slope factor, a soil-salinity factor
 * or a market-proximity factor means adding one class and adjusting weights — no edit to the service
 * that combines them, and no growing {@code if} chain.</p>
 *
 * <p>Every implementation must satisfy two contracts, both enforced at startup by
 * {@code ScoringFactorValidator}:</p>
 * <ul>
 *   <li>{@link #score} returns a value in {@code [0, 100]} for <em>any</em> input, including nulls</li>
 *   <li>{@link #weight} returns a value in {@code (0, 1]}, and all weights together sum to 1.0</li>
 * </ul>
 */
public interface ScoringFactor {

    /** Stable identifier, used in score breakdowns and log output. */
    String name();

    /** Human-readable explanation shown to the user in a score breakdown. */
    String description();

    /** Share of the composite score, in {@code (0, 1]}. All factors must sum to exactly 1.0. */
    double weight();

    /**
     * Normalised sub-score for this factor.
     *
     * @return a value in {@code [0, 100]}. Must never throw, and must handle absent inputs — an
     *     unsurveyed parcel is a normal case, not an error.
     */
    double score(ScoringContext context);
}
