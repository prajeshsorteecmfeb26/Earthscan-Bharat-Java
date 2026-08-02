package com.earthscan.land.service;

import com.earthscan.common.event.LandListedEvent;
import com.earthscan.common.exception.BadRequestException;
import com.earthscan.common.exception.ForbiddenException;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.messaging.EventPublisher;
import com.earthscan.common.messaging.RabbitTopology;
import com.earthscan.common.security.AuthenticatedUser;
import com.earthscan.common.security.RoleName;
import com.earthscan.land.domain.Land;
import com.earthscan.land.domain.ListingStatus;
import com.earthscan.land.domain.SoilType;
import com.earthscan.land.dto.InvestmentAnalysisResponse;
import com.earthscan.land.dto.LandRequest;
import com.earthscan.land.dto.LandResponse;
import com.earthscan.land.dto.LandSearchCriteria;
import com.earthscan.land.repository.LandRepository;
import com.earthscan.land.repository.LandSpecifications;
import com.earthscan.land.repository.SoilTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Listing CRUD, search and investment analysis. */
@Service
public class LandService {

    private static final Logger log = LoggerFactory.getLogger(LandService.class);

    /** Comparable parcels are within ±50% of the subject's area. */
    private static final double COMPARABLE_SIZE_TOLERANCE = 0.5;

    /** Beyond ±15% of the district mean, a listing is called under- or overpriced. */
    private static final double PRICING_TOLERANCE_PERCENT = 15.0;

    private static final int MIN_COMPARABLES_FOR_VERDICT = 3;

    private final LandRepository landRepository;
    private final SoilTypeRepository soilTypeRepository;
    private final LandScoringUseCase scoringService;
    private final EventPublisher eventPublisher;

    public LandService(LandRepository landRepository,
                       SoilTypeRepository soilTypeRepository,
                       LandScoringUseCase scoringService,
                       EventPublisher eventPublisher) {
        this.landRepository = landRepository;
        this.soilTypeRepository = soilTypeRepository;
        this.scoringService = scoringService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<LandResponse> search(LandSearchCriteria criteria, Pageable pageable) {
        if (criteria.hasInvertedPriceRange()) {
            throw new BadRequestException("minPrice cannot be greater than maxPrice");
        }
        if (criteria.hasInvertedSizeRange()) {
            throw new BadRequestException("minSize cannot be greater than maxSize");
        }
        return landRepository.findAll(LandSpecifications.matching(criteria), pageable)
                .map(LandResponse::from);
    }

    @Transactional(readOnly = true)
    public LandResponse findById(Long id) {
        return LandResponse.from(getLandOrThrow(id));
    }

    /**
     * Explains how a listing's score was arrived at, factor by factor.
     *
     * <p>Added because an unexplained 62/100 is not decision support. Showing that water access
     * scored 20/100 and carries 30% of the total is what lets a buyer judge whether the weighting
     * matches their own priorities.</p>
     */
    @Transactional(readOnly = true)
    public com.earthscan.land.service.scoring.ScoreBreakdown explainScore(Long id) {
        return scoringService.explain(getLandOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<LandResponse> findMyListings(Long ownerId) {
        return landRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(LandResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> findDistricts() {
        return landRepository.findDistinctDistricts();
    }

    /**
     * Creates a listing owned by the caller.
     *
     * <p>Ownership is taken from the authenticated principal, never from the request body. Scores are
     * computed server-side for the same reason — both are decisions the client is not entitled to
     * make.</p>
     */
    @Transactional
    public LandResponse create(LandRequest request, AuthenticatedUser owner) {
        SoilType soilType = resolveSoilType(request.soilType());

        Land land = new Land();
        apply(request, land, soilType);
        land.setOwnerId(owner.getId());
        land.setStatus(ListingStatus.ACTIVE);

        scoringService.score(land);
        Land saved = landRepository.save(land);

        log.info("Created land id={} by owner id={} (score={})",
                saved.getId(), owner.getId(), saved.getLandIntelligenceScore());

        publishListedEvent(saved);
        return LandResponse.from(saved);
    }

    /**
     * Updates a listing.
     *
     * @throws ForbiddenException unless the caller owns the listing or is an administrator
     */
    @Transactional
    public LandResponse update(Long id, LandRequest request, AuthenticatedUser caller) {
        Land land = getLandOrThrow(id);
        requireOwnershipOrAdmin(land, caller);

        SoilType soilType = resolveSoilType(request.soilType());
        apply(request, land, soilType);

        // Any change to soil, water or rainfall invalidates the previous score, so re-score
        // unconditionally rather than trying to detect which fields moved.
        scoringService.score(land);
        Land saved = landRepository.save(land);

        log.info("Updated land id={} by user id={}", id, caller.getId());
        return LandResponse.from(saved);
    }

    @Transactional
    public LandResponse updateStatus(Long id, ListingStatus status, AuthenticatedUser caller) {
        Land land = getLandOrThrow(id);
        requireOwnershipOrAdmin(land, caller);
        land.setStatus(status);
        log.info("Land id={} status set to {} by user id={}", id, status, caller.getId());
        return LandResponse.from(landRepository.save(land));
    }

    /** Verification is an administrative act: an owner must not be able to verify their own listing. */
    @Transactional
    public LandResponse setVerified(Long id, boolean verified, AuthenticatedUser caller) {
        if (!caller.isAdmin()) {
            throw new ForbiddenException("Only an administrator may verify a listing");
        }
        Land land = getLandOrThrow(id);
        land.setVerified(verified);
        log.info("Land id={} verified={} by admin id={}", id, verified, caller.getId());
        return LandResponse.from(landRepository.save(land));
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser caller) {
        Land land = getLandOrThrow(id);
        requireOwnershipOrAdmin(land, caller);
        landRepository.delete(land);
        log.info("Deleted land id={} by user id={}", id, caller.getId());
    }

    /**
     * Benchmarks a listing against comparable parcels in the same district.
     *
     * <p>Returns {@code INSUFFICIENT_DATA} rather than a verdict when fewer than
     * {@value #MIN_COMPARABLES_FOR_VERDICT} comparables exist. A "20% overpriced" claim derived from
     * one neighbouring listing is worse than no claim at all, because the user cannot tell how thin
     * the evidence was.</p>
     */
    @Transactional(readOnly = true)
    public InvestmentAnalysisResponse analyse(Long id) {
        Land land = getLandOrThrow(id);

        double size = land.getSizeInAcres() == null ? 0 : land.getSizeInAcres();
        List<Land> comparables = landRepository.findComparables(
                land.getDistrict(),
                land.getId(),
                size * (1 - COMPARABLE_SIZE_TOLERANCE),
                size * (1 + COMPARABLE_SIZE_TOLERANCE));

        Double districtAverageRaw = landRepository.findAveragePricePerAcreByDistrict(land.getDistrict());
        BigDecimal districtAverage = districtAverageRaw == null ? null
                : BigDecimal.valueOf(districtAverageRaw).setScale(2, RoundingMode.HALF_UP);

        BigDecimal pricePerAcre = land.getPricePerAcre();
        Double premiumPercent = null;
        String verdict = "INSUFFICIENT_DATA";
        String rationale;

        if (districtAverage == null || districtAverage.compareTo(BigDecimal.ZERO) <= 0
                || comparables.size() < MIN_COMPARABLES_FOR_VERDICT) {
            rationale = "Only %d comparable listing(s) were found in %s. At least %d are needed "
                    .formatted(comparables.size(), land.getDistrict(), MIN_COMPARABLES_FOR_VERDICT)
                    + "before a pricing verdict is meaningful.";
        } else {
            premiumPercent = pricePerAcre.subtract(districtAverage)
                    .divide(districtAverage, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP)
                    .doubleValue();

            if (premiumPercent < -PRICING_TOLERANCE_PERCENT) {
                verdict = "UNDERPRICED";
                rationale = "Priced %.1f%% below the %s average of %s per acre across %d comparable listings."
                        .formatted(Math.abs(premiumPercent), land.getDistrict(),
                                districtAverage.toPlainString(), comparables.size());
            } else if (premiumPercent > PRICING_TOLERANCE_PERCENT) {
                verdict = "OVERPRICED";
                rationale = "Priced %.1f%% above the %s average of %s per acre across %d comparable listings."
                        .formatted(premiumPercent, land.getDistrict(),
                                districtAverage.toPlainString(), comparables.size());
            } else {
                verdict = "FAIRLY_PRICED";
                rationale = "Within %.0f%% of the %s average of %s per acre across %d comparable listings."
                        .formatted(PRICING_TOLERANCE_PERCENT, land.getDistrict(),
                                districtAverage.toPlainString(), comparables.size());
            }
        }

        return new InvestmentAnalysisResponse(
                land.getId(),
                land.getTitle(),
                land.getPrice(),
                pricePerAcre,
                districtAverage,
                premiumPercent,
                land.getLandIntelligenceScore(),
                land.getBorewellSuccessProbability(),
                verdict,
                rationale,
                comparables.stream().map(LandResponse::from).toList());
    }

    // ---------------------------------------------------------------- internals

    private Land getLandOrThrow(Long id) {
        return landRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Land", id));
    }

    private void requireOwnershipOrAdmin(Land land, AuthenticatedUser caller) {
        if (caller.isAdmin() || land.isOwnedBy(caller.getId())) {
            return;
        }
        // Logged because a burst of these is a probe, not a user mistake.
        log.warn("User id={} attempted to modify land id={} owned by id={}",
                caller.getId(), land.getId(), land.getOwnerId());
        throw new ForbiddenException("You may only modify your own listings");
    }

    private SoilType resolveSoilType(String name) {
        return soilTypeRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new BadRequestException(
                        "Unknown soil type '" + name
                                + "'. Call GET /api/lands/soil-types for the supported values."));
    }

    private void apply(LandRequest request, Land land, SoilType soilType) {
        land.setTitle(request.title().trim());
        land.setDescription(request.description());
        land.setLocation(request.location().trim());
        land.setDistrict(request.district().trim());
        if (request.state() != null && !request.state().isBlank()) {
            land.setState(request.state().trim());
        }
        land.setLatitude(request.latitude());
        land.setLongitude(request.longitude());
        land.setPrice(request.price());
        land.setSizeInAcres(request.sizeInAcres());
        land.setSoilType(soilType);
        land.setGroundwaterLevelDepth(request.groundwaterLevelDepth());
        land.setAnnualRainfallMm(request.annualRainfallMm());
        land.setIrrigationAvailable(request.irrigationAvailable());
        land.setRoadAccess(request.roadAccess());
    }

    private void publishListedEvent(Land land) {
        LandListedEvent event = new LandListedEvent();
        event.setLandId(land.getId());
        event.setTitle(land.getTitle());
        event.setLocation(land.getLocation());
        event.setOwnerId(land.getOwnerId());
        event.setPrice(land.getPrice());
        event.setSizeInAcres(land.getSizeInAcres());
        event.setLandIntelligenceScore(land.getLandIntelligenceScore());
        event.setBorewellSuccessProbability(land.getBorewellSuccessProbability());
        eventPublisher.publish(RabbitTopology.ROUTING_LAND_LISTED, event);
    }

    /** Exposed for the caller-role check in the controller layer. */
    public static boolean canCreateListings(AuthenticatedUser user) {
        return user.hasRole(RoleName.FARMER) || user.hasRole(RoleName.ADMIN);
    }
}
