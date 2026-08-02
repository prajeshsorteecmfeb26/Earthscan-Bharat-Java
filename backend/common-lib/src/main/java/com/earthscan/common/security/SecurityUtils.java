package com.earthscan.common.security;

import com.earthscan.common.exception.UnauthorizedException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessors for the principal placed in the context by {@link JwtAuthenticationFilter}. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AuthenticatedUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof AuthenticatedUser user ? Optional.of(user) : Optional.empty();
    }

    /**
     * @throws UnauthorizedException when no authenticated principal is present. Endpoints reached
     *     through the security chain always have one, so this signals a misconfigured route.
     */
    public static AuthenticatedUser requireCurrentUser() {
        return currentUser().orElseThrow(
                () -> new UnauthorizedException("No authenticated user in the security context"));
    }

    public static Long currentUserId() {
        return requireCurrentUser().getId();
    }
}
