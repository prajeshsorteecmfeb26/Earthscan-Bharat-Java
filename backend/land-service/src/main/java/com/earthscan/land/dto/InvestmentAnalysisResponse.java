package com.earthscan.land.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

/**
 * Investment view of a single listing, benchmarked against comparable parcels in the same district.
 *
 * <p>Backs the {@code InvestmentAnalysis.jsx} page, which currently renders hard-coded figures.</p>
 */
@Schema(name = "InvestmentAnalysisResponse")
public record InvestmentAnalysisResponse(
        Long landId,
        String title,
        BigDecimal price,
        BigDecimal pricePerAcre,
        @Schema(description = "Mean price per acre of active listings in the same district")
        BigDecimal districtAveragePricePerAcre,
        @Schema(description = "Signed percentage difference from the district average. "
                + "Negative means this parcel is cheaper than its peers.")
        Double premiumOverDistrictPercent,
        Double landIntelligenceScore,
        Double borewellSuccessProbability,
        @Schema(example = "FAIRLY_PRICED",
                allowableValues = {"UNDERPRICED", "FAIRLY_PRICED", "OVERPRICED", "INSUFFICIENT_DATA"})
        String verdict,
        String rationale,
        @Schema(description = "Comparable active listings used to compute the benchmark")
        List<LandResponse> comparables) {
}
