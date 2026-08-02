package com.earthscan.auth.web;

import com.earthscan.auth.dto.MessageResponse;
import com.earthscan.auth.dto.UpdateRoleRequest;
import com.earthscan.auth.dto.UserResponse;
import com.earthscan.auth.dto.UserStatsResponse;
import com.earthscan.auth.service.UserAdminService;
import com.earthscan.common.exception.ApiErrorResponse;
import com.earthscan.common.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator-only user management.
 *
 * <p>{@code @PreAuthorize} sits on the class, so every method inherits the ADMIN requirement and a
 * newly added endpoint is protected by default rather than by remembering to annotate it. The
 * gateway also blocks unauthenticated traffic to {@code /api/admin/**}, but this check is what
 * actually enforces the role, and it holds even if the service is reached directly.</p>
 */
@RestController
@RequestMapping("/api/admin")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Administration", description = "User management. Requires the Admin role.")
public class AdminController {

    private final UserAdminService userAdminService;

    public AdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/users")
    @Operation(summary = "List every user",
            description = "Unpaged, matching the existing admin table. Prefer /users/page at scale.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Users returned"),
            @ApiResponse(responseCode = "403", description = "Caller is not an administrator",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userAdminService.findAll());
    }

    @GetMapping("/users/page")
    @Operation(summary = "Search users, paged",
            description = "Case-insensitive match on name or email. Sort with e.g. ?sort=createdAt,desc")
    public ResponseEntity<Page<UserResponse>> searchUsers(
            @Parameter(description = "Free-text term matched against name and email")
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return ResponseEntity.ok(userAdminService.search(q, pageable));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Fetch a single user")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable @Positive(message = "User id must be positive") Long id) {
        return ResponseEntity.ok(userAdminService.findById(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "User counts by role",
            description = "Aggregated in SQL so the analytics page no longer downloads every user "
                    + "row just to count them in the browser.")
    public ResponseEntity<UserStatsResponse> stats() {
        return ResponseEntity.ok(userAdminService.stats());
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Change a user's role",
            description = "Publishes UserRoleChangedEvent. Refuses to demote the last administrator.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Role updated"),
            @ApiResponse(responseCode = "400", description = "Would demote the last administrator",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> updateUserRole(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        userAdminService.updateRole(id, request, SecurityUtils.currentUserId());
        return ResponseEntity.ok(MessageResponse.of("User role updated successfully"));
    }

    @PatchMapping("/users/{id}/enabled")
    @Operation(summary = "Enable or disable an account",
            description = "A reversible alternative to deletion: the account cannot log in but its "
                    + "listings and forum history survive.")
    public ResponseEntity<UserResponse> setEnabled(
            @PathVariable @Positive Long id,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(
                userAdminService.setEnabled(id, enabled, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete a user",
            description = "Publishes UserDeletedEvent so land-service removes their listings and "
                    + "forum-service anonymises their posts. Refuses to delete the caller's own "
                    + "account or the last administrator.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User deleted"),
            @ApiResponse(responseCode = "400", description = "Self-deletion, or the last administrator",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such user",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> deleteUser(@PathVariable @Positive Long id) {
        userAdminService.deleteUser(id, SecurityUtils.currentUserId());
        return ResponseEntity.ok(MessageResponse.of("User deleted successfully"));
    }
}
