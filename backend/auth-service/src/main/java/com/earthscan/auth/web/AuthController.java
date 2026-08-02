package com.earthscan.auth.web;

import com.earthscan.auth.dto.AuthResponse;
import com.earthscan.auth.dto.LoginRequest;
import com.earthscan.auth.dto.MessageResponse;
import com.earthscan.auth.dto.RegisterRequest;
import com.earthscan.auth.dto.ResetPasswordRequest;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.service.AuthService;
import com.earthscan.common.exception.ApiErrorResponse;
import com.earthscan.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints.
 *
 * <p>Paths and payload shapes are identical to the ASP.NET {@code AuthController} they replace, so
 * the React client works against this service with only its base URL changed.</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login and password reset")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @SecurityRequirements   // Explicitly public: overrides the global bearer requirement in Swagger.
    @Operation(summary = "Register a new account",
            description = "Creates an account with one of the four platform roles and publishes "
                    + "a UserRegisteredEvent on the message bus.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MessageResponse.of("User registered successfully"));
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Authenticate and obtain a JWT",
            description = "Returns a signed HS256 token plus the user profile. The token must be sent "
                    + "as 'Authorization: Bearer <token>' on all protected endpoints.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Set a new password",
            description = "Feature parity with the original implementation. Note that this endpoint "
                    + "performs no ownership verification and is rate-limited for that reason; see "
                    + "the security notes in the README before exposing it publicly.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "404", description = "No account for that email",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(MessageResponse.of("Password reset successfully"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Fetch the caller's own profile",
            description = "Resolves the user from the token subject. Useful for the frontend to "
                    + "re-validate a token held in localStorage on page load.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile returned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<UserResponse> currentUser() {
        return ResponseEntity.ok(authService.currentUser(SecurityUtils.currentUserId()));
    }
}
