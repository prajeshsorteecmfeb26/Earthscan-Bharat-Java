package com.earthscan.land.web;

import com.earthscan.common.exception.ApiErrorResponse;
import com.earthscan.common.security.SecurityUtils;
import com.earthscan.land.dto.SavedSearchRequest;
import com.earthscan.land.dto.SavedSearchResponse;
import com.earthscan.land.service.SavedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saved searches and shortlisted listings, scoped to the authenticated user.
 *
 * <p>No endpoint takes a user id: it always comes from the token. An endpoint that accepted
 * {@code ?userId=} would let any authenticated user read anyone else's shortlist.</p>
 */
@RestController
@RequestMapping("/api/saved-searches")
@Validated
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('LAND_BUYER', 'FARMER', 'ADMIN')")
@Tag(name = "Saved Searches", description = "A user's saved search criteria and shortlisted listings")
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    public SavedSearchController(SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    @GetMapping
    @Operation(summary = "List the caller's saved searches")
    public ResponseEntity<List<SavedSearchResponse>> list() {
        return ResponseEntity.ok(savedSearchService.findForUser(SecurityUtils.currentUserId()));
    }

    @PostMapping
    @Operation(summary = "Save a search or shortlist a listing",
            description = "Supply landId to shortlist a specific listing, or the filter fields to "
                    + "save reusable criteria that new listings can be matched against.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Saved"),
            @ApiResponse(responseCode = "400", description = "Inverted price range, or per-user limit reached",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Listing already shortlisted",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SavedSearchResponse> create(@Valid @RequestBody SavedSearchRequest request) {
        SavedSearchResponse created =
                savedSearchService.create(request, SecurityUtils.currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a saved search",
            description = "Returns 404 for an id that belongs to another user, deliberately not 403.")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        savedSearchService.delete(id, SecurityUtils.currentUserId());
        return ResponseEntity.noContent().build();
    }
}
