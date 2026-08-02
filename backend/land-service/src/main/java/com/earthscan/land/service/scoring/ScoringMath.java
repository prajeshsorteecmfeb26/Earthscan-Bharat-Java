package com.earthscan.land.service.scoring;

/** Shared normalisation helpers, so each factor does not reimplement clamping and interpolation. */
final class ScoringMath {

    private ScoringMath() {
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Linear interpolation to 0-100 where a <em>lower</em> input is better.
     *
     * @param best  at or below this input, the score is 100
     * @param worst at or above this input, the score is 0
     */
    static double descendingScore(double value, double best, double worst) {
        if (value <= best) {
            return 100.0;
        }
        if (value >= worst) {
            return 0.0;
        }
        return 100.0 * (1.0 - (value - best) / (worst - best));
    }

    /**
     * Linear interpolation to 0-100 where a <em>higher</em> input is better.
     *
     * @param floor   at or below this input, the score is 0
     * @param ceiling at or above this input, the score is 100
     */
    static double ascendingScore(double value, double floor, double ceiling) {
        if (value >= ceiling) {
            return 100.0;
        }
        if (value <= floor) {
            return 0.0;
        }
        return 100.0 * (value - floor) / (ceiling - floor);
    }
}
