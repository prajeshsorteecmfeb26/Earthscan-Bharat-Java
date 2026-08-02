package com.earthscan.forum.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "CreateCommentRequest")
public record CreateCommentRequest(

        @NotBlank(message = "Content is required")
        @Size(min = 2, max = 3000, message = "Comment must be between 2 and 3000 characters")
        @Schema(example = "This looks like a nitrogen deficiency. A foliar spray of 2% urea should help.")
        String content) {
}
