package com.earthscan.land.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.earthscan.common.event.IntegrationEvent;
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
import com.earthscan.land.dto.LandRequest;
import com.earthscan.land.dto.LandResponse;
import com.earthscan.land.dto.LandSearchCriteria;
import com.earthscan.land.repository.LandRepository;
import com.earthscan.land.repository.SoilTypeRepository;
import com.earthscan.land.service.scoring.AccessibilityFactor;
import com.earthscan.land.service.scoring.BorewellProbabilityCalculator;
import com.earthscan.land.service.scoring.MoistureRetentionFactor;
import com.earthscan.land.service.scoring.RainfallFactor;
import com.earthscan.land.service.scoring.SoilFertilityFactor;
import com.earthscan.land.service.scoring.WaterAccessFactor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LandService")
class LandServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final Long ADMIN_ID = 1L;

    @Mock
    private LandRepository landRepository;
    @Mock
    private SoilTypeRepository soilTypeRepository;
    @Mock
    private EventPublisher eventPublisher;

    private LandService landService;

    @BeforeEach
    void setUp() {
        // The scoring engine is pure computation, so it is used for real rather than mocked -
        // mocking it would assert only that a method was called, not that scores are sane.
        RainfallFactor rainfall = new RainfallFactor();
        LandScoringService scoring = new LandScoringService(
                java.util.List.of(new SoilFertilityFactor(), new WaterAccessFactor(), rainfall,
                        new MoistureRetentionFactor(), new AccessibilityFactor()),
                new BorewellProbabilityCalculator(rainfall));
        scoring.validateFactors();
        landService = new LandService(landRepository, soilTypeRepository, scoring, eventPublisher);
    }

    // ------------------------------------------------------------------ fixtures

    private static AuthenticatedUser user(Long id, RoleName role) {
        return new AuthenticatedUser(id, "Test User", "test@example.com", List.of(role));
    }

    private static SoilType blackCotton() {
        return new SoilType("Black Cotton", 82, 85, "fixture");
    }

    private static Land existingLand(Long id, Long ownerId) {
        Land land = new Land();
        land.setTitle("Existing parcel");
        land.setLocation("Baramati, Pune");
        land.setDistrict("Pune");
        land.setPrice(new BigDecimal("5000000"));
        land.setSizeInAcres(5.0);
        land.setSoilType(blackCotton());
        land.setGroundwaterLevelDepth(40.0);
        land.setAnnualRainfallMm(700);
        land.setOwnerId(ownerId);
        land.setStatus(ListingStatus.ACTIVE);
        try {
            Field idField = Land.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(land, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return land;
    }

    private static LandRequest request() {
        return new LandRequest(
                "Fertile black cotton farmland", "A good parcel", "Baramati, Pune", "Pune",
                "Maharashtra", 18.15, 74.58, new BigDecimal("6500000"), 5.0,
                "Black Cotton", 35.0, 720, true, true);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("takes ownership from the token, not the request body")
        void assignsOwnerFromToken() {
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.of(blackCotton()));
            when(landRepository.save(any(Land.class))).thenAnswer(inv -> inv.getArgument(0));

            landService.create(request(), user(OWNER_ID, RoleName.FARMER));

            ArgumentCaptor<Land> saved = ArgumentCaptor.forClass(Land.class);
            verify(landRepository).save(saved.capture());
            assertThat(saved.getValue().getOwnerId()).isEqualTo(OWNER_ID);
        }

        @Test
        @DisplayName("computes scores server-side so a client cannot inflate its own listing")
        void computesScoresServerSide() {
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.of(blackCotton()));
            when(landRepository.save(any(Land.class))).thenAnswer(inv -> inv.getArgument(0));

            LandResponse response = landService.create(request(), user(OWNER_ID, RoleName.FARMER));

            // The DTO has no score fields at all, so the only possible source is the engine.
            assertThat(response.landIntelligenceScore()).isNotNull().isBetween(0.0, 100.0);
            assertThat(response.borewellSuccessProbability()).isNotNull().isBetween(10.0, 95.0);
        }

        @Test
        @DisplayName("publishes LandListedEvent after saving")
        void publishesEvent() {
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.of(blackCotton()));
            when(landRepository.save(any(Land.class))).thenAnswer(inv -> inv.getArgument(0));

            landService.create(request(), user(OWNER_ID, RoleName.FARMER));

            ArgumentCaptor<IntegrationEvent> captor = ArgumentCaptor.forClass(IntegrationEvent.class);
            verify(eventPublisher).publish(eq(RabbitTopology.ROUTING_LAND_LISTED), captor.capture());
            LandListedEvent event = (LandListedEvent) captor.getValue();
            assertThat(event.getOwnerId()).isEqualTo(OWNER_ID);
            assertThat(event.getLandIntelligenceScore()).isNotNull();
        }

        @Test
        @DisplayName("rejects an unknown soil type with a helpful message")
        void rejectsUnknownSoilType() {
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> landService.create(request(), user(OWNER_ID, RoleName.FARMER)))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Unknown soil type")
                    .hasMessageContaining("/api/lands/soil-types");

            verify(landRepository, never()).save(any());
            verify(eventPublisher, never()).publish(anyString(), any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("allows the owner to update their own listing")
        void ownerCanUpdate() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.of(blackCotton()));
            when(landRepository.save(land)).thenReturn(land);

            LandResponse response = landService.update(5L, request(), user(OWNER_ID, RoleName.FARMER));

            assertThat(response.title()).isEqualTo("Fertile black cotton farmland");
        }

        @Test
        @DisplayName("allows an administrator to update any listing")
        void adminCanUpdateAnyListing() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.of(blackCotton()));
            when(landRepository.save(land)).thenReturn(land);

            assertThat(landService.update(5L, request(), user(ADMIN_ID, RoleName.ADMIN))).isNotNull();
        }

        @Test
        @DisplayName("refuses a farmer editing someone else's listing")
        void strangerCannotUpdate() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));

            assertThatThrownBy(() ->
                    landService.update(5L, request(), user(OTHER_USER_ID, RoleName.FARMER)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("your own listings");

            verify(landRepository, never()).save(any());
        }

        @Test
        @DisplayName("re-scores on update, so a changed water table changes the score")
        void reScoresOnUpdate() {
            Land land = existingLand(5L, OWNER_ID);
            Double originalScore = land.getLandIntelligenceScore();
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(soilTypeRepository.findByNameIgnoreCase("Black Cotton"))
                    .thenReturn(Optional.of(blackCotton()));
            when(landRepository.save(land)).thenReturn(land);

            landService.update(5L, request(), user(OWNER_ID, RoleName.FARMER));

            assertThat(land.getScoredAt()).isNotNull();
            assertThat(land.getLandIntelligenceScore()).isNotEqualTo(originalScore);
        }

        @Test
        @DisplayName("returns 404 for a listing that does not exist")
        void failsForMissingListing() {
            when(landRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    landService.update(404L, request(), user(OWNER_ID, RoleName.FARMER)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("allows the owner to delete their own listing")
        void ownerCanDelete() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));

            landService.delete(5L, user(OWNER_ID, RoleName.FARMER));

            verify(landRepository).delete(land);
        }

        @Test
        @DisplayName("refuses deletion by a user who does not own the listing")
        void strangerCannotDelete() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));

            assertThatThrownBy(() -> landService.delete(5L, user(OTHER_USER_ID, RoleName.FARMER)))
                    .isInstanceOf(ForbiddenException.class);

            verify(landRepository, never()).delete(any(Land.class));
        }
    }

    @Nested
    @DisplayName("setVerified")
    class SetVerified {

        @Test
        @DisplayName("refuses to let an owner verify their own listing")
        void ownerCannotSelfVerify() {
            // The verified badge is a trust signal for buyers; if owners could award it to
            // themselves it would mean nothing.
            assertThatThrownBy(() ->
                    landService.setVerified(5L, true, user(OWNER_ID, RoleName.FARMER)))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("administrator");

            verify(landRepository, never()).findById(any());
        }

        @Test
        @DisplayName("allows an administrator to verify a listing")
        void adminCanVerify() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(landRepository.save(land)).thenReturn(land);

            assertThat(landService.setVerified(5L, true, user(ADMIN_ID, RoleName.ADMIN)).verified())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("rejects an inverted price range instead of silently returning nothing")
        void rejectsInvertedPriceRange() {
            LandSearchCriteria criteria = new LandSearchCriteria(
                    null, null, null,
                    new BigDecimal("9000000"), new BigDecimal("1000000"),
                    null, null, null, null, null);

            assertThatThrownBy(() -> landService.search(criteria, org.springframework.data.domain.Pageable.unpaged()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("minPrice cannot be greater than maxPrice");
        }

        @Test
        @DisplayName("rejects an inverted size range")
        void rejectsInvertedSizeRange() {
            LandSearchCriteria criteria = new LandSearchCriteria(
                    null, null, null, null, null, 20.0, 5.0, null, null, null);

            assertThatThrownBy(() -> landService.search(criteria, org.springframework.data.domain.Pageable.unpaged()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("minSize cannot be greater than maxSize");
        }
    }

    @Nested
    @DisplayName("analyse")
    class Analyse {

        @Test
        @DisplayName("withholds a verdict when there are too few comparables")
        void refusesVerdictOnThinData() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(landRepository.findComparables(any(), any(), any(), any()))
                    .thenReturn(List.of(existingLand(6L, OTHER_USER_ID)));
            when(landRepository.findAveragePricePerAcreByDistrict("Pune")).thenReturn(1_000_000.0);

            var analysis = landService.analyse(5L);

            // One comparable is not evidence. Saying so is more useful than a confident wrong number.
            assertThat(analysis.verdict()).isEqualTo("INSUFFICIENT_DATA");
            assertThat(analysis.premiumOverDistrictPercent()).isNull();
            assertThat(analysis.rationale()).contains("At least 3");
        }

        @Test
        @DisplayName("calls a listing overpriced when it sits well above the district average")
        void flagsOverpriced() {
            Land land = existingLand(5L, OWNER_ID);   // 5,000,000 over 5 acres = 1,000,000/acre
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(landRepository.findComparables(any(), any(), any(), any())).thenReturn(List.of(
                    existingLand(6L, OTHER_USER_ID),
                    existingLand(7L, OTHER_USER_ID),
                    existingLand(8L, OTHER_USER_ID)));
            when(landRepository.findAveragePricePerAcreByDistrict("Pune")).thenReturn(500_000.0);

            var analysis = landService.analyse(5L);

            assertThat(analysis.verdict()).isEqualTo("OVERPRICED");
            assertThat(analysis.premiumOverDistrictPercent()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("calls a listing fairly priced when it is close to the district average")
        void flagsFairlyPriced() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(landRepository.findComparables(any(), any(), any(), any())).thenReturn(List.of(
                    existingLand(6L, OTHER_USER_ID),
                    existingLand(7L, OTHER_USER_ID),
                    existingLand(8L, OTHER_USER_ID)));
            when(landRepository.findAveragePricePerAcreByDistrict("Pune")).thenReturn(1_000_000.0);

            var analysis = landService.analyse(5L);

            assertThat(analysis.verdict()).isEqualTo("FAIRLY_PRICED");
            assertThat(analysis.premiumOverDistrictPercent()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("calls a listing underpriced when it sits well below the district average")
        void flagsUnderpriced() {
            Land land = existingLand(5L, OWNER_ID);
            when(landRepository.findById(5L)).thenReturn(Optional.of(land));
            when(landRepository.findComparables(any(), any(), any(), any())).thenReturn(List.of(
                    existingLand(6L, OTHER_USER_ID),
                    existingLand(7L, OTHER_USER_ID),
                    existingLand(8L, OTHER_USER_ID)));
            when(landRepository.findAveragePricePerAcreByDistrict("Pune")).thenReturn(2_000_000.0);

            var analysis = landService.analyse(5L);

            assertThat(analysis.verdict()).isEqualTo("UNDERPRICED");
            assertThat(analysis.premiumOverDistrictPercent()).isEqualTo(-50.0);
        }
    }
}
