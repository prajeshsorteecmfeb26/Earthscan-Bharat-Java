package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest")
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Schema(example = "anita@example.com")
        String email,

        @NotBlank(message = "Password is required")
        @Schema(example = "harvest2026")
        String password) {
}
