package com.earthscan.auth.dto;

import com.earthscan.auth.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * User projection returned to the client.
 *
 * <p>{@code role} is the display name ({@code "Land Buyer"}), which is exactly what the existing
 * React code stores in {@code localStorage} and compares against in {@code ProtectedRoute}. Keeping
 * that shape is why the frontend needs no changes to its authorisation logic.</p>
 */
@Schema(name = "UserResponse")
public record UserResponse(
        Long id,
        String name,
        String email,
        @Schema(example = "Farmer") String role,
        boolean enabled,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPrimaryRoleDisplayName(),
                user.isEnabled(),
                user.getCreatedAt());
    }
}
