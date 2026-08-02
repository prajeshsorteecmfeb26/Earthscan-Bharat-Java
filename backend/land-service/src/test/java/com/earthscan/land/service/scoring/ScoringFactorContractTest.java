package com.earthscan.land.service.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.earthscan.land.service.LandScoringService;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Contract tests applied to <em>every</em> {@link ScoringFactor} implementation.
 *
 * <p>This is the test that makes the Strategy pattern safe to extend. A new factor added by someone
 * else inherits all of these assertions the moment it is added to {@link #allFactors()}, so it cannot
 * quietly break the composite score by returning 150, or NaN, or by throwing on a null input.</p>
 *
 * <p>The alternative — testing each factor in isolation and hoping the next author remembers the
 * invariants — is how the 0-100 contract erodes.</p>
 */
@DisplayName("ScoringFactor contract")
class ScoringFactorContractTest {

    private static final RainfallFactor RAINFALL = new RainfallFactor();

    static List<ScoringFactor> allFactors() {
        return List.of(
                new SoilFertilityFactor(),
                new WaterAccessFactor(),
                RAINFALL,
                new MoistureRetentionFactor(),
                new AccessibilityFactor());
    }

    static Stream<Arguments> factorsAndContexts() {
        return allFactors().stream().flatMap(factor ->
                extremeContexts().stream().map(context -> Arguments.of(factor, context)));
    }

    /** Deliberately hostile inputs: nulls, negatives, zeros and values past every threshold. */
    static List<ScoringContext> extremeContexts() {
        return List.of(
                ScoringContext.builder().build(),                       // everything absent
                ScoringContext.builder()
                        .soilFertilityIndex(0).soilWaterRetentionIndex(0)
                        .groundwaterDepthMetres(0.0).annualRainfallMm(0).build(),
                ScoringContext.builder()
                        .soilFertilityIndex(100).soilWaterRetentionIndex(100)
                        .groundwaterDepthMetres(1000.0).annualRainfallMm(12000)
                        .irrigationAvailable(true).roadAccess(true).build(),
                // Out-of-range values that a bad database row could produce
                ScoringContext.builder()
                        .soilFertilityIndex(-50).soilWaterRetentionIndex(500)
                        .groundwaterDepthMetres(-10.0).annualRainfallMm(-100).build(),
                ScoringContext.builder()
                        .soilFertilityIndex(60).soilWaterRetentionIndex(60)
                        .groundwaterDepthMetres(30.0).annualRainfallMm(400).build(),
                ScoringContext.builder()
                        .soilFertilityIndex(60).soilWaterRetentionIndex(60)
                        .groundwaterDepthMetres(250.0).annualRainfallMm(1200).build());
    }

    @ParameterizedTest(name = "{0} stays within 0-100")
    @MethodSource("factorsAndContexts")
    @DisplayName("every factor returns 0-100 for every input, including absent and out-of-range")
    void scoreStaysInRange(ScoringFactor factor, ScoringContext context) {
        double score = factor.score(context);

        assertThat(score).isBetween(0.0, 100.0);
        assertThat(Double.isNaN(score)).isFalse();
    }

    @ParameterizedTest(name = "{0} never throws")
    @MethodSource("factorsAndContexts")
    @DisplayName("no factor throws on absent inputs — an unsurveyed parcel is normal, not an error")
    void scoreNeverThrows(ScoringFactor factor, ScoringContext context) {
        assertThat(factor.score(context)).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("allFactors")
    @DisplayName("every factor declares a weight in (0, 1]")
    void weightIsValid(ScoringFactor factor) {
        assertThat(factor.weight()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @ParameterizedTest
    @MethodSource("allFactors")
    @DisplayName("every factor declares a non-blank name and description")
    void metadataIsPresent(ScoringFactor factor) {
        assertThat(factor.name()).isNotBlank();
        assertThat(factor.description()).isNotBlank();
    }

    @Test
    @DisplayName("factor names are unique, so a breakdown cannot contain ambiguous rows")
    void namesAreUnique() {
        List<String> names = allFactors().stream().map(ScoringFactor::name).toList();

        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("the registered weights sum to exactly 1.0")
    void weightsSumToOne() {
        double total = allFactors().stream().mapToDouble(ScoringFactor::weight).sum();

        assertThat(total).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("startup fails when weights do not sum to 1.0, rather than serving off-scale scores")
    void rejectsWeightsThatDoNotSumToOne() {
        // A weight set that does not sum to one produces a score that is not on a 0-100 scale.
        // Refusing to start is the correct response; silently serving it is not.
        ScoringFactor rogue = new ScoringFactor() {
            public String name() {
                return "rogue";
            }

            public String description() {
                return "adds unaccounted weight";
            }

            public double weight() {
                return 0.5;
            }

            public double score(ScoringContext context) {
                return 100;
            }
        };

        List<ScoringFactor> overweight = Stream.concat(allFactors().stream(), Stream.of(rogue)).toList();
        LandScoringService service =
                new LandScoringService(overweight, new BorewellProbabilityCalculator(RAINFALL));

        assertThatThrownBy(service::validateFactors)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must sum to 1.0")
                .hasMessageContaining("rogue");
    }

    @Test
    @DisplayName("startup fails when no factors are registered")
    void rejectsEmptyFactorSet() {
        LandScoringService service =
                new LandScoringService(List.of(), new BorewellProbabilityCalculator(RAINFALL));

        assertThatThrownBy(service::validateFactors)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No ScoringFactor beans");
    }

    @Test
    @DisplayName("startup fails when a factor declares a weight outside (0, 1]")
    void rejectsOutOfRangeWeight() {
        ScoringFactor zeroWeight = new ScoringFactor() {
            public String name() {
                return "inert";
            }

            public String description() {
                return "contributes nothing";
            }

            public double weight() {
                return 0.0;
            }

            public double score(ScoringContext context) {
                return 50;
            }
        };

        // Sums to 1.0 overall, so only the per-factor check can catch this one.
        LandScoringService service = new LandScoringService(
                Stream.concat(allFactors().stream(), Stream.of(zeroWeight)).toList(),
                new BorewellProbabilityCalculator(RAINFALL));

        assertThatThrownBy(service::validateFactors)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside (0, 1]");
    }

    @Test
    @DisplayName("a misbehaving factor is clamped rather than corrupting the composite score")
    void clampsMisbehavingFactor() {
        // Defence in depth: the interface requires 0-100, but a future factor could break it.
        // One bad factor must degrade its own contribution, not push the whole score off scale.
        ScoringFactor broken = new ScoringFactor() {
            public String name() {
                return "broken";
            }

            public String description() {
                return "violates the 0-100 contract";
            }

            public double weight() {
                return 1.0;
            }

            public double score(ScoringContext context) {
                return 100_000;
            }
        };

        LandScoringService service =
                new LandScoringService(List.of(broken), new BorewellProbabilityCalculator(RAINFALL));
        service.validateFactors();

        assertThat(service.computeIntelligenceScore(ScoringContext.builder().build()))
                .isEqualTo(100.0);
    }

    @Test
    @DisplayName("a factor returning NaN contributes zero instead of poisoning the total")
    void handlesNaN() {
        ScoringFactor nan = new ScoringFactor() {
            public String name() {
                return "nan";
            }

            public String description() {
                return "returns NaN";
            }

            public double weight() {
                return 1.0;
            }

            public double score(ScoringContext context) {
                return Double.NaN;
            }
        };

        LandScoringService service =
                new LandScoringService(List.of(nan), new BorewellProbabilityCalculator(RAINFALL));
        service.validateFactors();

        // Without the guard, NaN propagates through the sum and every comparison against the score
        // silently returns false - the listing would vanish from filtered searches.
        assertThat(service.computeIntelligenceScore(ScoringContext.builder().build())).isZero();
    }
}
