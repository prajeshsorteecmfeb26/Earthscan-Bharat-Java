package com.earthscan.land.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Schema(name = "SavedSearchRequest")
public record SavedSearchRequest(

        @NotBlank(message = "Label is required")
        @Size(max = 150, message = "Label must not exceed 150 characters")
        @Schema(example = "Black cotton land near Baramati under 60L")
        String label,

        @Size(max = 200)
        String locationQuery,

        @Size(max = 60)
        String soilTypeName,

        @DecimalMin(value = "0", message = "minPrice cannot be negative")
        BigDecimal minPrice,

        @DecimalMin(value = "0", message = "maxPrice cannot be negative")
        BigDecimal maxPrice,

        @Min(value = 0, message = "minScore must be between 0 and 100")
        @Max(value = 100, message = "minScore must be between 0 and 100")
        Double minScore,

        @Schema(description = "Set to shortlist one specific listing instead of saving criteria")
        Long landId,

        @Schema(defaultValue = "true",
                description = "Whether to raise a notification when a new listing matches")
        Boolean notifyOnMatch) {
}
