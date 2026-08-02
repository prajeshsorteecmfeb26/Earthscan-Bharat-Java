package com.earthscan.land.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Payload for creating or updating a listing.
 *
 * <p>Notably absent: {@code landIntelligenceScore}, {@code borewellSuccessProbability} and
 * {@code ownerId}. The original controller bound the {@code Land} entity directly from the request
 * body, which meant a caller could POST their own scores — and set {@code OwnerId} to anyone. Using a
 * dedicated DTO makes those fields unreachable from outside: scores come from the scoring engine,
 * ownership from the JWT.</p>
 */
@Schema(name = "LandRequest")
public record LandRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 150, message = "Title must be between 5 and 150 characters")
        @Schema(example = "Fertile black-cotton farmland with canal access")
        String title,

        @Size(max = 2000, message = "Description must not exceed 2000 characters")
        String description,

        @NotBlank(message = "Location is required")
        @Size(max = 200)
        @Schema(example = "Baramati, Pune")
        String location,

        @NotBlank(message = "District is required")
        @Size(max = 80)
        @Schema(example = "Pune")
        String district,

        @Size(max = 80)
        @Schema(example = "Maharashtra", defaultValue = "Maharashtra")
        String state,

        // India spans roughly 6-38°N, 68-98°E. Bounding the values rejects transposed
        // latitude/longitude pairs, which otherwise silently drop a pin in the Indian Ocean.
        @DecimalMin(value = "6.0", message = "Latitude must be within India (6-38)")
        @DecimalMax(value = "38.0", message = "Latitude must be within India (6-38)")
        Double latitude,

        @DecimalMin(value = "68.0", message = "Longitude must be within India (68-98)")
        @DecimalMax(value = "98.0", message = "Longitude must be within India (68-98)")
        Double longitude,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "1000.0", message = "Price must be at least 1000")
        @Digits(integer = 16, fraction = 2, message = "Price may have at most 2 decimal places")
        @Schema(example = "5000000.00")
        BigDecimal price,

        @NotNull(message = "Size is required")
        @Positive(message = "Size must be greater than zero")
        @DecimalMax(value = "100000.0", message = "Size must not exceed 100000 acres")
        @Schema(example = "5.5")
        Double sizeInAcres,

        @NotBlank(message = "Soil type is required")
        @Schema(example = "Black Cotton",
                description = "Must match a name in the soil_types table; see GET /api/lands/soil-types")
        String soilType,

        @NotNull(message = "Groundwater depth is required")
        @Min(value = 0, message = "Groundwater depth cannot be negative")
        @Max(value = 1000, message = "Groundwater depth beyond 1000m is not plausible")
        @Schema(example = "45.0", description = "Metres below ground level")
        Double groundwaterLevelDepth,

        @Min(value = 0, message = "Rainfall cannot be negative")
        @Max(value = 12000, message = "Annual rainfall beyond 12000mm is not plausible")
        @Schema(example = "750", description = "Long-term average annual rainfall in mm")
        Integer annualRainfallMm,

        boolean irrigationAvailable,

        boolean roadAccess) {
}
