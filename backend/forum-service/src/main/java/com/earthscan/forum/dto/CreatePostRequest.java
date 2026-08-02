package com.earthscan.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * New thread payload.
 *
 * <p>The original DTO accepted three unbounded strings with no constraints, so an empty post or a
 * multi-megabyte one were both valid. Bounding {@code content} matters more here than elsewhere
 * because these strings are embedded in a document that MongoDB caps at 16MB.</p>
 */
@Schema(name = "CreatePostRequest")
public record CreatePostRequest(

        @NotBlank(message = "Title is required")
        @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
        @Schema(example = "Yellowing leaves on soybean at flowering stage")
        String title,

        @NotBlank(message = "Content is required")
        @Size(min = 10, max = 5000, message = "Content must be between 10 and 5000 characters")
        @Schema(example = "My soybean crop in Latur has started yellowing from the lower leaves...")
        String content,

        @NotBlank(message = "Category is required")
        @Size(max = 50, message = "Category must not exceed 50 characters")
        @Schema(example = "Crop Health")
        String category) {
}
