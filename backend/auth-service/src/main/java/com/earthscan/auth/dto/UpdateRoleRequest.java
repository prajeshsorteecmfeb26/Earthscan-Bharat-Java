package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(name = "UpdateRoleRequest")
public record UpdateRoleRequest(

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "Farmer|Land Buyer|Agriculture Expert|Admin",
                message = "Role must be one of: Farmer, Land Buyer, Agriculture Expert, Admin")
        @Schema(example = "Agriculture Expert")
        String role) {
}
