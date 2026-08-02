package com.earthscan.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Login result. The {@code {token, user}} shape is what {@code AuthContext.login()} destructures
 * today, so it is preserved verbatim.
 */
@Schema(name = "AuthResponse")
public record AuthResponse(
        @Schema(description = "Signed HS256 JWT, valid for 7 days") String token,
        @Schema(description = "Seconds until the token expires") long expiresInSeconds,
        UserResponse user) {
}
