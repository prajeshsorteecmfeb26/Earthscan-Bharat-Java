package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Password reset payload.
 *
 * <p>This carries over a real weakness from the original implementation: knowing an email address is
 * enough to change that account's password, with no token, no link and no proof of ownership. It is
 * reproduced here to keep feature parity, but the endpoint is rate-limited and the README flags it
 * as the first thing to fix before any public deployment.</p>
 */
@Schema(name = "ResetPasswordRequest")
public record ResetPasswordRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        String email,

        @NotBlank(message = "New password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = ".*[A-Za-z].*", message = "Password must contain at least one letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain at least one digit")
        String newPassword) {
}
