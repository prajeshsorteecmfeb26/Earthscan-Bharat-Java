package com.earthscan.land.service;

import com.earthscan.land.domain.Land;
import com.earthscan.land.service.scoring.BorewellProbabilityCalculator;
import com.earthscan.land.service.scoring.ScoreBreakdown;
import com.earthscan.land.service.scoring.ScoringContext;
import com.earthscan.land.service.scoring.ScoringFactor;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Composes the registered {@link ScoringFactor} strategies into the Land Intelligence Score.
 *
 * <p>This class no longer knows how any individual factor is calculated — it only knows how to
 * combine them. Adding a soil-salinity or slope factor is a new {@code @Component} plus a weight
 * adjustment, with no edit here. That is the Open/Closed win the Strategy refactor was for; the
 * previous version had all five calculations inlined as private methods, so every new factor meant
 * editing the class that combined them.</p>
 */
@Slf4j
@Service
public class LandScoringService implements LandScoringUseCase {

    private static final double WEIGHT_TOLERANCE = 1e-9;

    private final List<ScoringFactor> factors;
    private final BorewellProbabilityCalculator borewellCalculator;

    /**
     * @param factors every {@link ScoringFactor} bean on the classpath, injected by Spring
     */
    public LandScoringService(List<ScoringFactor> factors,
                              BorewellProbabilityCalculator borewellCalculator) {
        this.factors = factors.stream()
                // Deterministic order so score breakdowns and logs are stable across restarts;
                // classpath scanning order is not guaranteed.
                .sorted(Comparator.comparingDouble(ScoringFactor::weight).reversed()
                        .thenComparing(ScoringFactor::name))
                .toList();
        this.borewellCalculator = borewellCalculator;
    }

    /**
     * Validates the factor set at startup.
     *
     * <p>Runs as {@code @PostConstruct} rather than in a static initialiser because the factors are
     * injected. Failing here prevents the service from starting, which is the correct outcome: a
     * weight set that does not sum to one produces scores that are not on a 0-100 scale, and silently
     * serving those is worse than not starting.</p>
     */
    @PostConstruct
    public void validateFactors() {
        if (factors.isEmpty()) {
            throw new IllegalStateException(
                    "No ScoringFactor beans were found. Component scanning must cover "
                            + "com.earthscan.land.service.scoring.");
        }

        double total = factors.stream().mapToDouble(ScoringFactor::weight).sum();
        if (Math.abs(total - 1.0) > WEIGHT_TOLERANCE) {
            throw new IllegalStateException(
                    "ScoringFactor weights must sum to 1.0 but sum to %s. Factors: %s"
                            .formatted(total, factors.stream().map(ScoringFactor::name).toList()));
        }

        factors.stream()
                .filter(factor -> factor.weight() <= 0 || factor.weight() > 1)
                .findFirst()
                .ifPresent(bad -> {
                    throw new IllegalStateException(
                            "ScoringFactor '%s' has weight %s, which is outside (0, 1]"
                                    .formatted(bad.name(), bad.weight()));
                });

        log.info("Land scoring initialised with {} factor(s): {}",
                factors.size(),
                factors.stream()
                        .map(f -> f.name() + "=" + (int) (f.weight() * 100) + "%")
                        .toList());
    }

    @Override
    public void score(Land land) {
        ScoringContext context = ScoringContext.from(land);
        double intelligence = computeIntelligenceScore(context);
        double borewell = borewellCalculator.calculate(context);
        land.applyScores(round(intelligence), round(borewell));

        log.debug("Scored land '{}': intelligence={} borewell={}",
                land.getTitle(), land.getLandIntelligenceScore(),
                land.getBorewellSuccessProbability());
    }

    @Override
    public double computeIntelligenceScore(ScoringContext context) {
        double weighted = factors.stream()
                .mapToDouble(factor -> clampFactorScore(factor, context) * factor.weight())
                .sum();
        return clamp(weighted);
    }

    @Override
    public double computeBorewellProbability(ScoringContext context) {
        return borewellCalculator.calculate(context);
    }

    @Override
    public ScoreBreakdown explain(Land land) {
        ScoringContext context = ScoringContext.from(land);

        List<ScoreBreakdown.FactorContribution> contributions = factors.stream()
                .map(factor -> {
                    double score = clampFactorScore(factor, context);
                    return ScoreBreakdown.FactorContribution.builder()
                            .name(factor.name())
                            .description(factor.description())
                            .weight(factor.weight())
                            .score(round(score))
                            .weightedContribution(round(score * factor.weight()))
                            .build();
                })
                .toList();

        return ScoreBreakdown.builder()
                .totalScore(round(computeIntelligenceScore(context)))
                .borewellSuccessProbability(round(borewellCalculator.calculate(context)))
                .factors(contributions)
                .build();
    }

    /**
     * Defends the composite against a misbehaving factor.
     *
     * <p>The interface requires a 0-100 return, but a third-party or newly added factor could break
     * that contract. Clamping here means one bad factor degrades its own contribution instead of
     * pushing the whole score off scale.
     */
    private double clampFactorScore(ScoringFactor factor, ScoringContext context) {
        double raw = factor.score(context);
        if (raw < 0 || raw > 100 || Double.isNaN(raw)) {
            log.warn("ScoringFactor '{}' returned {}, which violates the 0-100 contract. Clamping.",
                    factor.name(), raw);
        }
        return Double.isNaN(raw) ? 0.0 : Math.max(0.0, Math.min(100.0, raw));
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    /** One decimal place: the model's precision does not justify implying more. */
    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
