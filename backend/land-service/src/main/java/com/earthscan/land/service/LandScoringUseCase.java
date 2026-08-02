package com.earthscan.land.service;

import com.earthscan.land.domain.Land;
import com.earthscan.land.service.scoring.ScoreBreakdown;
import com.earthscan.land.service.scoring.ScoringContext;

/**
 * Scoring abstraction depended on by {@code LandService}. <strong>Dependency Inversion.</strong>
 *
 * <p>The consumer depends on this interface rather than on {@code LandScoringService}, so the scoring
 * implementation can be swapped — for a calibrated statistical model, or a stub in a test — without
 * touching the code that persists listings.</p>
 */
public interface LandScoringUseCase {

    /** Computes both derived scores and writes them onto the entity. */
    void score(Land land);

    /** Composite Land Intelligence Score, 0-100. */
    double computeIntelligenceScore(ScoringContext context);

    /** Borewell success probability, 10-95. */
    double computeBorewellProbability(ScoringContext context);

    /** The score together with each factor's contribution, for display to a user. */
    ScoreBreakdown explain(Land land);
}
