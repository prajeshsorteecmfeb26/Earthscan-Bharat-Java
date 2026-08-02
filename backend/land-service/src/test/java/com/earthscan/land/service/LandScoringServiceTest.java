package com.earthscan.land.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.earthscan.land.domain.Land;
import com.earthscan.land.domain.SoilType;
import com.earthscan.land.service.scoring.AccessibilityFactor;
import com.earthscan.land.service.scoring.BorewellProbabilityCalculator;
import com.earthscan.land.service.scoring.MoistureRetentionFactor;
import com.earthscan.land.service.scoring.RainfallFactor;
import com.earthscan.land.service.scoring.ScoringContext;
import com.earthscan.land.service.scoring.ScoringFactor;
import com.earthscan.land.service.scoring.SoilFertilityFactor;
import com.earthscan.land.service.scoring.WaterAccessFactor;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the scoring engine.
 *
 * <p>No mocks: this class is pure computation, so the tests assert on real arithmetic. The emphasis
 * is on monotonicity and bounds rather than exact values — asserting that a specific input yields
 * exactly 73.4 would break on every legitimate recalibration, while "wetter land never scores worse
 * than drier land" must hold for any calibration and is the property that actually matters.</p>
 */
@DisplayName("LandScoringService")
class LandScoringServiceTest {

    private LandScoringService service;

    @BeforeEach
    void setUp() {
        // Real factors, wired by hand rather than by Spring. These are pure functions with no
        // collaborators, so mocking them would assert only that a list was iterated - not that the
        // arithmetic is right, which is the whole point of this class.
        RainfallFactor rainfall = new RainfallFactor();
        List<ScoringFactor> factors = List.of(
                new SoilFertilityFactor(),
                new WaterAccessFactor(),
                rainfall,
                new MoistureRetentionFactor(),
                new AccessibilityFactor());
        service = new LandScoringService(factors, new BorewellProbabilityCalculator(rainfall));
        service.validateFactors();
    }

    // ------------------------------------------------------------------ fixtures

    private static SoilType soil(int fertility, int retention) {
        return new SoilType("Test Soil", fertility, retention, "fixture");
    }

    private static Land land(SoilType soilType, double waterDepth, Integer rainfall,
                             boolean irrigation, boolean road) {
        Land land = new Land();
        land.setTitle("Test parcel");
        land.setLocation("Test");
        land.setDistrict("Pune");
        land.setPrice(new java.math.BigDecimal("1000000"));
        land.setSizeInAcres(5.0);
        land.setSoilType(soilType);
        land.setGroundwaterLevelDepth(waterDepth);
        land.setAnnualRainfallMm(rainfall);
        land.setIrrigationAvailable(irrigation);
        land.setRoadAccess(road);
        land.setOwnerId(1L);
        return land;
    }

    /** A mid-range parcel used as the baseline for comparative assertions. */
    private static Land averageLand() {
        return land(soil(60, 60), 80.0, 800, false, true);
    }

    // The service now works in terms of ScoringContext rather than the entity, so the tests adapt
    // once here instead of at every call site.
    private double computeIntelligence(Land land) {
        return service.computeIntelligenceScore(ScoringContext.from(land));
    }

    private double computeBorewell(Land land) {
        return service.computeBorewellProbability(ScoringContext.from(land));
    }

    @Nested
    @DisplayName("computeIntelligenceScore")
    class IntelligenceScore {

        @Test
        @DisplayName("awards the maximum to an ideal parcel")
        void perfectLandScoresAtCeiling() {
            Land ideal = land(soil(100, 100), 10.0, 1500, true, true);

            assertThat(computeIntelligence(ideal)).isEqualTo(100.0);
        }

        @Test
        @DisplayName("awards the minimum to the worst possible parcel")
        void worstLandScoresAtFloor() {
            Land worst = land(soil(0, 0), 400.0, 100, false, false);

            assertThat(computeIntelligence(worst)).isEqualTo(0.0);
        }

        @ParameterizedTest(name = "fertility {0} scores no worse than fertility {1}")
        @CsvSource({"100,50", "80,40", "60,20", "50,49"})
        @DisplayName("is monotonic in soil fertility")
        void isMonotonicInFertility(int higher, int lower) {
            double higherScore = computeIntelligence(
                    land(soil(higher, 60), 80.0, 800, false, true));
            double lowerScore = computeIntelligence(
                    land(soil(lower, 60), 80.0, 800, false, true));

            assertThat(higherScore).isGreaterThan(lowerScore);
        }

        @ParameterizedTest(name = "water at {0}m scores no worse than water at {1}m")
        @CsvSource({"20,60", "50,150", "100,200", "30,31"})
        @DisplayName("is monotonic in groundwater depth: shallower is never worse")
        void isMonotonicInWaterDepth(double shallower, double deeper) {
            double shallowScore = computeIntelligence(
                    land(soil(60, 60), shallower, 800, false, true));
            double deepScore = computeIntelligence(
                    land(soil(60, 60), deeper, 800, false, true));

            assertThat(shallowScore).isGreaterThanOrEqualTo(deepScore);
        }

        @Test
        @DisplayName("rewards irrigation and road access")
        void rewardsAccessibility() {
            double withAccess = computeIntelligence(
                    land(soil(60, 60), 80.0, 800, true, true));
            double withoutAccess = computeIntelligence(
                    land(soil(60, 60), 80.0, 800, false, false));

            assertThat(withAccess).isGreaterThan(withoutAccess);
        }

        @Test
        @DisplayName("treats unknown rainfall as neutral rather than penalising the listing")
        void unknownRainfallIsNeutral() {
            double unknown = computeIntelligence(
                    land(soil(60, 60), 80.0, null, false, true));
            double neutral = computeIntelligence(
                    land(soil(60, 60), 80.0, 800, false, true));

            // 800mm sits close to the midpoint of the rainfall band, so the two should be close.
            assertThat(unknown).isCloseTo(neutral, org.assertj.core.data.Offset.offset(3.0));
        }

        @ParameterizedTest
        @ValueSource(doubles = {0.0, 15.0, 30.0, 75.0, 150.0, 250.0, 500.0, 1000.0})
        @DisplayName("always stays within 0-100 across the full depth range")
        void staysWithinBounds(double depth) {
            double score = computeIntelligence(
                    land(soil(50, 50), depth, 700, false, false));

            assertThat(score).isBetween(0.0, 100.0);
        }

        @Test
        @DisplayName("distinguishes soils that the old Contains(\"Black\") check scored identically")
        void distinguishesBlackSoilVariants() {
            // The original engine added a flat +20 for any soil whose name contained "Black", so
            // Deep Black and Shallow Black scored the same despite very different water retention.
            Land deepBlack = land(new SoilType("Deep Black", 88, 90, ""), 80.0, 800, false, true);
            Land shallowBlack = land(new SoilType("Shallow Black", 55, 50, ""), 80.0, 800, false, true);

            assertThat(computeIntelligence(deepBlack))
                    .isGreaterThan(computeIntelligence(shallowBlack));
        }
    }

    @Nested
    @DisplayName("computeBorewellProbability")
    class BorewellProbability {

        @Test
        @DisplayName("never promises certainty, even for very shallow water")
        void neverReturnsOneHundred() {
            Land shallow = land(soil(100, 100), 1.0, 3000, true, true);

            // No heuristic can guarantee water; a 100% figure would be a false promise to a farmer
            // about to spend real money on drilling.
            assertThat(computeBorewell(shallow)).isLessThanOrEqualTo(95.0);
        }

        @Test
        @DisplayName("never returns zero, since deep drilling occasionally succeeds")
        void neverReturnsZero() {
            Land veryDeep = land(soil(0, 0), 800.0, 100, false, false);

            assertThat(computeBorewell(veryDeep)).isGreaterThanOrEqualTo(10.0);
        }

        @Test
        @DisplayName("decays smoothly with depth instead of stepping between buckets")
        void decaysSmoothly() {
            // The original returned exactly 90% at 49.9m and 65% at 50.0m — a 25-point cliff over
            // 10cm. Adjacent depths must now produce adjacent probabilities.
            double at49 = computeBorewell(
                    land(soil(60, 60), 49.9, 800, false, true));
            double at50 = computeBorewell(
                    land(soil(60, 60), 50.1, 800, false, true));

            assertThat(Math.abs(at49 - at50)).isLessThan(1.0);
        }

        @ParameterizedTest(name = "{0}m yields at least as much as {1}m")
        @CsvSource({"20,80", "50,120", "100,180", "30,31", "200,300"})
        @DisplayName("is monotonically decreasing in depth")
        void isMonotonic(double shallower, double deeper) {
            double shallow = computeBorewell(
                    land(soil(60, 60), shallower, 800, false, false));
            double deep = computeBorewell(
                    land(soil(60, 60), deeper, 800, false, false));

            assertThat(shallow).isGreaterThanOrEqualTo(deep);
        }

        @Test
        @DisplayName("gives high-retention soil a modest recharge bonus")
        void rewardsWaterRetention() {
            double highRetention = computeBorewell(
                    land(soil(60, 95), 100.0, 800, false, false));
            double lowRetention = computeBorewell(
                    land(soil(60, 20), 100.0, 800, false, false));

            assertThat(highRetention).isGreaterThan(lowRetention);
        }

        @Test
        @DisplayName("keeps depth dominant over the recharge modifiers")
        void depthDominatesModifiers() {
            // Excellent soil and rainfall must not rescue a parcel with a 250m water table above a
            // parcel with water at 20m and poor soil, or the score would mislead badly.
            double deepButGoodSoil = computeBorewell(
                    land(soil(100, 100), 250.0, 3000, true, true));
            double shallowButPoorSoil = computeBorewell(
                    land(soil(10, 10), 20.0, 300, false, false));

            assertThat(shallowButPoorSoil).isGreaterThan(deepButGoodSoil);
        }

        @Test
        @DisplayName("treats a missing depth pessimistically rather than optimistically")
        void missingDepthIsPessimistic() {
            Land unknown = land(soil(60, 60), 80.0, 800, false, false);
            unknown.setGroundwaterLevelDepth(null);

            // Encouraging someone to drill on the basis of absent data would be the wrong default.
            assertThat(computeBorewell(unknown)).isLessThan(30.0);
        }
    }

    @Nested
    @DisplayName("score")
    class ApplyScores {

        @Test
        @DisplayName("writes both scores and a scoring timestamp onto the entity")
        void appliesBothScores() {
            Land land = averageLand();
            assertThat(land.getScoredAt()).isNull();

            service.score(land);

            assertThat(land.getLandIntelligenceScore()).isBetween(0.0, 100.0);
            assertThat(land.getBorewellSuccessProbability()).isBetween(10.0, 95.0);
            assertThat(land.getScoredAt()).isNotNull();
        }

        @Test
        @DisplayName("rounds to one decimal place, not implying false precision")
        void roundsToOneDecimal() {
            Land land = averageLand();

            service.score(land);

            assertThat(land.getLandIntelligenceScore() * 10)
                    .isEqualTo(Math.round(land.getLandIntelligenceScore() * 10));
        }

        @Test
        @DisplayName("is deterministic: the same inputs always give the same scores")
        void isDeterministic() {
            Land first = averageLand();
            Land second = averageLand();

            service.score(first);
            service.score(second);

            assertThat(first.getLandIntelligenceScore())
                    .isEqualTo(second.getLandIntelligenceScore());
            assertThat(first.getBorewellSuccessProbability())
                    .isEqualTo(second.getBorewellSuccessProbability());
        }
    }
}
