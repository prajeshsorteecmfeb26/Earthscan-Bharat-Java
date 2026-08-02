package com.earthscan.land.service.scoring;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;

/**
 * The composite score together with each factor's contribution.
 *
 * <p>Returned by a dedicated endpoint so a user can see <em>why</em> a parcel scored what it did.
 * An unexplained 62/100 is not decision support; "water access scored 20/100 and carries 30% of the
 * total" is.</p>
 */
@Builder
@Schema(name = "ScoreBreakdown")
public record ScoreBreakdown(

        @Schema(description = "Composite Land Intelligence Score, 0-100", example = "72.4")
        double totalScore,

        @Schema(description = "Estimated borewell success probability, 10-95", example = "64.0")
        double borewellSuccessProbability,

        List<FactorContribution> factors) {

    /** One factor's normalised sub-score and its weighted contribution to the total. */
    @Builder
    @Schema(name = "FactorContribution")
    public record FactorContribution(
            @Schema(example = "waterAccess") String name,
            @Schema(example = "Depth to the water table; shallower is better") String description,
            @Schema(description = "Share of the composite score", example = "0.3") double weight,
            @Schema(description = "This factor's normalised score, 0-100", example = "20.0") double score,
            @Schema(description = "score x weight", example = "6.0") double weightedContribution) {
    }
}
