package com.earthscan.land.web;

import com.earthscan.common.exception.ApiErrorResponse;
import com.earthscan.common.security.AuthenticatedUser;
import com.earthscan.common.security.SecurityUtils;
import com.earthscan.land.domain.ListingStatus;
import com.earthscan.land.dto.InvestmentAnalysisResponse;
import com.earthscan.land.dto.LandRequest;
import com.earthscan.land.dto.LandResponse;
import com.earthscan.land.dto.LandSearchCriteria;
import com.earthscan.land.dto.SoilTypeResponse;
import com.earthscan.land.service.LandService;
import com.earthscan.land.service.SoilTypeService;
import com.earthscan.land.service.scoring.ScoreBreakdown;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Land listing endpoints.
 *
 * <p>Reads are public — browsing listings before signing up is the point of a marketplace. Writes
 * require a Farmer or Admin role, since a Land Buyer has no business creating listings.</p>
 */
@RestController
@RequestMapping("/api/lands")
@Validated
@Tag(name = "Lands", description = "Listing search, detail, scoring and investment analysis")
public class LandController {

    private final LandService landService;
    private final SoilTypeService soilTypeService;

    public LandController(LandService landService, SoilTypeService soilTypeService) {
        this.landService = landService;
        this.soilTypeService = soilTypeService;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Search listings",
            description = "All filters are optional and combine with AND. Only ACTIVE listings are "
                    + "returned. Sort with e.g. ?sort=landIntelligenceScore,desc")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching listings"),
            @ApiResponse(responseCode = "400", description = "Inverted price or size range",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Page<LandResponse>> search(
            @Valid @ModelAttribute LandSearchCriteria criteria,
            @PageableDefault(size = 12, sort = "landIntelligenceScore") Pageable pageable) {
        return ResponseEntity.ok(landService.search(criteria, pageable));
    }

    @GetMapping("/soil-types")
    @SecurityRequirements
    @Operation(summary = "List supported soil types",
            description = "Reference data for the listing form. The fertility and water-retention "
                    + "indices shown here are the inputs the scoring engine uses.")
    public ResponseEntity<List<SoilTypeResponse>> soilTypes() {
        return ResponseEntity.ok(soilTypeService.findAll());
    }

    @GetMapping("/districts")
    @SecurityRequirements
    @Operation(summary = "List districts that currently have listings")
    public ResponseEntity<List<String>> districts() {
        return ResponseEntity.ok(landService.findDistricts());
    }

    @GetMapping("/mine")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the caller's own listings")
    public ResponseEntity<List<LandResponse>> myListings() {
        return ResponseEntity.ok(landService.findMyListings(SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Fetch one listing")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listing found"),
            @ApiResponse(responseCode = "404", description = "No such listing",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LandResponse> findById(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(landService.findById(id));
    }

    @GetMapping("/{id}/analysis")
    @SecurityRequirements
    @Operation(summary = "Investment analysis for a listing",
            description = "Benchmarks price per acre against comparable active listings in the same "
                    + "district. Returns INSUFFICIENT_DATA when too few comparables exist to justify "
                    + "a verdict.")
    public ResponseEntity<InvestmentAnalysisResponse> analyse(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(landService.analyse(id));
    }

    @GetMapping("/{id}/score-breakdown")
    @SecurityRequirements
    @Operation(summary = "Explain how a listing's score was calculated",
            description = "Returns each scoring factor's normalised sub-score, its weight and its "
                    + "weighted contribution to the composite total. Exists so the score is "
                    + "auditable by the user rather than an unexplained number.")
    public ResponseEntity<ScoreBreakdown> scoreBreakdown(@PathVariable @Positive Long id) {
        return ResponseEntity.ok(landService.explainScore(id));
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
    @Operation(summary = "Create a listing",
            description = "Owner is taken from the token, and both scores are computed server-side; "
                    + "neither can be supplied by the caller. Publishes a LandListedEvent.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Listing created"),
            @ApiResponse(responseCode = "400", description = "Validation failed or unknown soil type",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Caller is not a Farmer or Admin",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LandResponse> create(@Valid @RequestBody LandRequest request) {
        AuthenticatedUser caller = SecurityUtils.requireCurrentUser();
        LandResponse created = landService.create(request, caller);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
    @Operation(summary = "Update a listing",
            description = "Permitted for the owner or an administrator. Re-runs the scoring engine.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Listing updated"),
            @ApiResponse(responseCode = "403", description = "Caller does not own this listing",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No such listing",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LandResponse> update(@PathVariable @Positive Long id,
                                              @Valid @RequestBody LandRequest request) {
        return ResponseEntity.ok(
                landService.update(id, request, SecurityUtils.requireCurrentUser()));
    }

    @PatchMapping("/{id}/status")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
    @Operation(summary = "Change listing status",
            description = "Marking a listing SOLD retains it for history while removing it from search.")
    public ResponseEntity<LandResponse> updateStatus(@PathVariable @Positive Long id,
                                                     @RequestParam ListingStatus status) {
        return ResponseEntity.ok(
                landService.updateStatus(id, status, SecurityUtils.requireCurrentUser()));
    }

    @PatchMapping("/{id}/verified")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Verify or unverify a listing",
            description = "Administrator only — an owner must not be able to award their own listing "
                    + "the verified badge buyers rely on.")
    public ResponseEntity<LandResponse> setVerified(@PathVariable @Positive Long id,
                                                    @RequestParam boolean verified) {
        return ResponseEntity.ok(
                landService.setVerified(id, verified, SecurityUtils.requireCurrentUser()));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAnyRole('FARMER', 'ADMIN')")
    @Operation(summary = "Delete a listing",
            description = "Permitted for the owner or an administrator.")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        landService.delete(id, SecurityUtils.requireCurrentUser());
        return ResponseEntity.noContent().build();
    }
}
