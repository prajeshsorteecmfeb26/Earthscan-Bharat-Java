package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload.
 *
 * <p>Field names match what the existing React {@code AuthContext.register()} already sends, so the
 * frontend contract is unchanged. What <em>is</em> new is that every field is validated — the
 * ASP.NET DTO had no constraints at all, so a blank name or a one-character password was accepted.</p>
 */
@Schema(name = "RegisterRequest")
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        @Schema(example = "Anita Deshmukh")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        @Schema(example = "anita@example.com")
        String email,

        /*
         * Length is the constraint that actually buys security here; the character-class rule is
         * kept modest on purpose. This platform's users are largely rural farmers on mobile
         * keyboards, and a rule demanding four character classes reliably pushes people towards
         * "Password@1" — worse in practice than a longer, simpler passphrase.
         */
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one digit")
        @Schema(example = "harvest2026")
        String password,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "Farmer|Land Buyer|Agriculture Expert|Admin",
                message = "Role must be one of: Farmer, Land Buyer, Agriculture Expert, Admin")
        @Schema(example = "Farmer",
                allowableValues = {"Farmer", "Land Buyer", "Agriculture Expert", "Admin"})
        String role) {
}
