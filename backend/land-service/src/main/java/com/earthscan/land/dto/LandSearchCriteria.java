package com.earthscan.land.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

/**
 * Optional search filters. Every field may be null, meaning "do not constrain on this".
 *
 * <p>Bound from query parameters, so a caller sends only what they care about:
 * {@code GET /api/lands?district=Pune&minScore=70}</p>
 */
@Schema(name = "LandSearchCriteria")
public record LandSearchCriteria(

        @Schema(description = "Free-text match against title, description and location")
        String q,

        String district,

        String soilType,

        @DecimalMin(value = "0", message = "minPrice cannot be negative")
        BigDecimal minPrice,

        @DecimalMin(value = "0", message = "maxPrice cannot be negative")
        BigDecimal maxPrice,

        @Min(value = 0, message = "minSize cannot be negative")
        Double minSize,

        @Min(value = 0, message = "maxSize cannot be negative")
        Double maxSize,

        @Min(value = 0, message = "minScore must be between 0 and 100")
        @Max(value = 100, message = "minScore must be between 0 and 100")
        Double minScore,

        Boolean irrigationAvailable,

        Boolean verifiedOnly) {

    /** True when the price range is inverted, which is a user error worth reporting explicitly. */
    public boolean hasInvertedPriceRange() {
        return minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0;
    }

    public boolean hasInvertedSizeRange() {
        return minSize != null && maxSize != null && minSize > maxSize;
    }
}
