package com.earthscan.land.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.earthscan.common.exception.BadRequestException;
import com.earthscan.common.exception.GlobalExceptionHandler;
import com.earthscan.common.exception.ResourceNotFoundException;
import com.earthscan.common.security.JwtTokenProvider;
import com.earthscan.land.dto.LandResponse;
import com.earthscan.land.dto.LandSearchCriteria;
import com.earthscan.land.service.LandService;
import com.earthscan.land.service.SoilTypeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-layer tests for {@link LandController}.
 *
 * <p>The pagination assertions are the ones that matter most here. Whether {@code ?page=2&size=5} is
 * actually bound into a {@link Pageable}, and whether {@code @PageableDefault} supplies the right
 * fallback, is invisible to a service test — the service just receives whatever {@code Pageable} it is
 * handed. Only a request-level test can prove the query parameters are wired.</p>
 */
@WebMvcTest(controllers = LandController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
@DisplayName("LandController (web slice)")
class LandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LandService landService;

    @MockBean
    private SoilTypeService soilTypeService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    private static LandResponse sampleLand() {
        return new LandResponse(42L, "Fertile black cotton farmland", "A good parcel",
                "Baramati, Pune", "Pune", "Maharashtra", 18.15, 74.58,
                new BigDecimal("6500000.00"), new BigDecimal("1300000.00"), 5.0,
                "Black Cotton", 35.0, 720, true, true, 81.5, 82.0, 10L, "ACTIVE", false,
                Instant.now());
    }

    @Nested
    @DisplayName("GET /api/lands")
    class Search {

        @Test
        @DisplayName("returns 200 with a paged body")
        void returnsPagedResults() throws Exception {
            Page<LandResponse> page = new PageImpl<>(List.of(sampleLand()),
                    PageRequest.of(0, 12), 1);
            when(landService.search(any(), any())).thenReturn(page);

            mockMvc.perform(get("/api/lands"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(42))
                    .andExpect(jsonPath("$.content[0].landIntelligenceScore").value(81.5))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("binds page, size and sort from the query string")
        void bindsPageable() throws Exception {
            when(landService.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

            mockMvc.perform(get("/api/lands")
                            .param("page", "2")
                            .param("size", "5")
                            .param("sort", "price,asc"))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(landService).search(any(), captor.capture());
            Pageable pageable = captor.getValue();

            assertThatPageable(pageable, 2, 5);
            org.assertj.core.api.Assertions.assertThat(pageable.getSort().getOrderFor("price"))
                    .isNotNull()
                    .extracting(Sort.Order::getDirection)
                    .isEqualTo(Sort.Direction.ASC);
        }

        @Test
        @DisplayName("applies the declared defaults when no paging parameters are sent")
        void appliesPageableDefaults() throws Exception {
            when(landService.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

            mockMvc.perform(get("/api/lands")).andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(landService).search(any(), captor.capture());

            // @PageableDefault(size = 12, sort = "landIntelligenceScore")
            assertThatPageable(captor.getValue(), 0, 12);
        }

        @Test
        @DisplayName("binds every optional filter into the criteria object")
        void bindsFilters() throws Exception {
            when(landService.search(any(), any()))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 12), 0));

            mockMvc.perform(get("/api/lands")
                            .param("q", "black cotton")
                            .param("district", "Pune")
                            .param("soilType", "Black Cotton")
                            .param("minPrice", "1000000")
                            .param("maxPrice", "9000000")
                            .param("minSize", "2")
                            .param("maxSize", "20")
                            .param("minScore", "70")
                            .param("irrigationAvailable", "true")
                            .param("verifiedOnly", "true"))
                    .andExpect(status().isOk());

            ArgumentCaptor<LandSearchCriteria> captor =
                    ArgumentCaptor.forClass(LandSearchCriteria.class);
            verify(landService).search(captor.capture(), any());
            LandSearchCriteria criteria = captor.getValue();

            org.assertj.core.api.Assertions.assertThat(criteria.q()).isEqualTo("black cotton");
            org.assertj.core.api.Assertions.assertThat(criteria.district()).isEqualTo("Pune");
            org.assertj.core.api.Assertions.assertThat(criteria.minScore()).isEqualTo(70.0);
            org.assertj.core.api.Assertions.assertThat(criteria.verifiedOnly()).isTrue();
        }

        @Test
        @DisplayName("returns 400 for an inverted price range instead of an empty result set")
        void rejectsInvertedRange() throws Exception {
            when(landService.search(any(), any()))
                    .thenThrow(new BadRequestException("minPrice cannot be greater than maxPrice"));

            // Silently returning zero results would look like "no such land exists" rather than
            // "your filter is contradictory".
            mockMvc.perform(get("/api/lands")
                            .param("minPrice", "9000000")
                            .param("maxPrice", "1000000"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value("minPrice cannot be greater than maxPrice"));
        }

        @Test
        @DisplayName("returns 400 for a minScore outside 0-100")
        void rejectsOutOfRangeScore() throws Exception {
            mockMvc.perform(get("/api/lands").param("minScore", "150"))
                    .andExpect(status().isBadRequest());

            verify(landService, never()).search(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/lands/{id}")
    class FindById {

        @Test
        @DisplayName("returns 200 for a known listing")
        void returns200() throws Exception {
            when(landService.findById(42L)).thenReturn(sampleLand());

            mockMvc.perform(get("/api/lands/42"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Fertile black cotton farmland"))
                    .andExpect(jsonPath("$.pricePerAcre").value(1300000.00));
        }

        @Test
        @DisplayName("returns 404 for an unknown listing")
        void returns404() throws Exception {
            when(landService.findById(999L))
                    .thenThrow(ResourceNotFoundException.of("Land", 999L));

            mockMvc.perform(get("/api/lands/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("returns 400 for a non-numeric id rather than a 500")
        void returns400ForBadId() throws Exception {
            mockMvc.perform(get("/api/lands/not-a-number"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 for a negative id")
        void returns400ForNegativeId() throws Exception {
            mockMvc.perform(get("/api/lands/-5"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/lands")
    class Create {

        private Map<String, Object> validBody() {
            return Map.ofEntries(
                    Map.entry("title", "Fertile black cotton farmland"),
                    Map.entry("description", "A good parcel"),
                    Map.entry("location", "Baramati, Pune"),
                    Map.entry("district", "Pune"),
                    Map.entry("state", "Maharashtra"),
                    Map.entry("latitude", 18.15),
                    Map.entry("longitude", 74.58),
                    Map.entry("price", 6500000),
                    Map.entry("sizeInAcres", 5.0),
                    Map.entry("soilType", "Black Cotton"),
                    Map.entry("groundwaterLevelDepth", 35.0),
                    Map.entry("annualRainfallMm", 720),
                    Map.entry("irrigationAvailable", true),
                    Map.entry("roadAccess", true));
        }

        @Test
        @DisplayName("returns 400 when latitude is outside India's bounding box")
        void rejectsTransposedCoordinates() throws Exception {
            Map<String, Object> body = new java.util.HashMap<>(validBody());
            // A transposed lat/long pair - 74.58 is a valid longitude but an impossible latitude.
            // Without the bound this silently drops a map pin in the Arctic.
            body.put("latitude", 74.58);
            body.put("longitude", 18.15);

            mockMvc.perform(post("/api/lands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.latitude").exists());
        }

        @Test
        @DisplayName("returns 400 for a zero or negative size")
        void rejectsNonPositiveSize() throws Exception {
            Map<String, Object> body = new java.util.HashMap<>(validBody());
            body.put("sizeInAcres", 0);

            mockMvc.perform(post("/api/lands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.sizeInAcres").exists());
        }

        @Test
        @DisplayName("returns 400 for an implausible groundwater depth")
        void rejectsImplausibleDepth() throws Exception {
            Map<String, Object> body = new java.util.HashMap<>(validBody());
            body.put("groundwaterLevelDepth", 5000);

            mockMvc.perform(post("/api/lands")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("a request body cannot set the score or the owner")
        void ignoresServerAuthoritativeFields() throws Exception {
            Map<String, Object> body = new java.util.HashMap<>(validBody());
            body.put("landIntelligenceScore", 100);
            body.put("borewellSuccessProbability", 100);
            body.put("ownerId", 1);
            body.put("verified", true);

            // LandRequest has no such fields, so Jackson has nowhere to bind them. This test exists
            // to catch someone "helpfully" adding them to the DTO later.
            String json = objectMapper.writeValueAsString(body);
            org.assertj.core.api.Assertions.assertThat(json).contains("landIntelligenceScore");

            mockMvc.perform(post("/api/lands")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json));

            ArgumentCaptor<com.earthscan.land.dto.LandRequest> captor =
                    ArgumentCaptor.forClass(com.earthscan.land.dto.LandRequest.class);
            verify(landService).create(captor.capture(), any());
            // The record simply has no accessor for them - which is the guarantee.
            org.assertj.core.api.Assertions.assertThat(captor.getValue().title())
                    .isEqualTo("Fertile black cotton farmland");
        }
    }

    @Nested
    @DisplayName("GET /api/lands/soil-types")
    class SoilTypes {

        @Test
        @DisplayName("returns the reference data unauthenticated")
        void returnsSoilTypes() throws Exception {
            when(soilTypeService.findAll()).thenReturn(List.of(
                    new com.earthscan.land.dto.SoilTypeResponse(1, "Black Cotton", 82, 85, "regur")));

            mockMvc.perform(get("/api/lands/soil-types"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("Black Cotton"))
                    .andExpect(jsonPath("$[0].fertilityIndex").value(82));
        }
    }

    private static void assertThatPageable(Pageable pageable, int expectedPage, int expectedSize) {
        org.assertj.core.api.Assertions.assertThat(pageable.getPageNumber()).isEqualTo(expectedPage);
        org.assertj.core.api.Assertions.assertThat(pageable.getPageSize()).isEqualTo(expectedSize);
    }
}
