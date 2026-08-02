package com.earthscan.land.config;

import com.earthscan.land.domain.Land;
import com.earthscan.land.domain.SoilType;
import com.earthscan.land.repository.LandRepository;
import com.earthscan.land.repository.SoilTypeRepository;
import com.earthscan.land.service.LandScoringService;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the {@code soil_types} reference table and, optionally, demo listings.
 *
 * <p>The soil rows are not optional — they are reference data the scoring engine and the listing form
 * both depend on, so they are seeded unconditionally and idempotently. Demo listings are gated behind
 * a flag that defaults to off, because seeding fake land prices into a real deployment would corrupt
 * the district averages the investment analysis is computed from.</p>
 *
 * <p>The fertility and water-retention figures below are ordinal calibrations for Maharashtra
 * cropping conditions, derived from general soil-classification characteristics rather than from a
 * soil survey. They are intended to rank soils sensibly against each other, and an agronomist can
 * correct any row directly in the database without a redeploy.</p>
 */
@Component
public class LandDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LandDataSeeder.class);

    private final SoilTypeRepository soilTypeRepository;
    private final LandRepository landRepository;
    private final LandScoringService scoringService;
    private final boolean seedDemoListings;

    public LandDataSeeder(SoilTypeRepository soilTypeRepository,
                          LandRepository landRepository,
                          LandScoringService scoringService,
                          @Value("${earthscan.bootstrap.seed-demo-listings:false}") boolean seedDemoListings) {
        this.soilTypeRepository = soilTypeRepository;
        this.landRepository = landRepository;
        this.scoringService = scoringService;
        this.seedDemoListings = seedDemoListings;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedSoilTypes();
        if (seedDemoListings) {
            seedDemoListings();
        }
    }

    private void seedSoilTypes() {
        List<SoilType> catalogue = List.of(
                new SoilType("Deep Black", 88, 90,
                        "Deep regur/black cotton soil. High clay content, excellent moisture "
                                + "retention, ideal for cotton, sugarcane and soybean."),
                new SoilType("Black Cotton", 82, 85,
                        "Classic Maharashtra regur soil. Very good retention; cracks when dry."),
                new SoilType("Medium Black", 72, 70,
                        "Moderate depth black soil. Good for pulses, jowar and cotton."),
                new SoilType("Shallow Black", 55, 50,
                        "Thin black soil over murum. Drought-prone; suits jowar and bajra."),
                new SoilType("Alluvial", 90, 75,
                        "River-deposited soil. Highly fertile and easily worked; suits most crops."),
                new SoilType("Red Loamy", 68, 60,
                        "Iron-rich red soil with loamy texture. Good for groundnut and millets."),
                new SoilType("Red Soil", 60, 52,
                        "Iron-rich, low in nitrogen and phosphorus. Responds well to fertiliser."),
                new SoilType("Laterite", 52, 45,
                        "Leached tropical soil of the Konkan belt. Acidic; suits cashew and mango."),
                new SoilType("Red Laterite", 58, 48,
                        "Konkan laterite with higher iron content. Alphonso mango country."),
                new SoilType("Loamy", 78, 68,
                        "Balanced sand-silt-clay mix. Versatile and well-drained."),
                new SoilType("Clayey", 70, 88,
                        "Heavy clay. Retains water strongly; well suited to paddy, poorly drained."),
                new SoilType("Sandy Loam", 50, 35,
                        "Light, free-draining soil. Needs frequent irrigation and organic matter."),
                new SoilType("Light Soil", 45, 32,
                        "Coarse, low organic content. Suits onion and other shallow-rooted crops."),
                new SoilType("Rocky", 20, 18,
                        "Substantial rock or murum content. Marginal for cultivation."),
                new SoilType("Mixed", 62, 58,
                        "Heterogeneous profile. Behaviour varies across the parcel."));

        int created = 0;
        for (SoilType soilType : catalogue) {
            if (!soilTypeRepository.existsByNameIgnoreCase(soilType.getName())) {
                soilTypeRepository.save(soilType);
                created++;
            }
        }
        if (created > 0) {
            log.info("Seeded {} soil type(s)", created);
        }
    }

    private void seedDemoListings() {
        if (landRepository.count() > 0) {
            return;   // Never append demo rows to a database that already holds listings.
        }
        SoilType blackCotton = soilTypeRepository.findByNameIgnoreCase("Black Cotton").orElseThrow();
        SoilType alluvial = soilTypeRepository.findByNameIgnoreCase("Alluvial").orElseThrow();
        SoilType redLaterite = soilTypeRepository.findByNameIgnoreCase("Red Laterite").orElseThrow();
        SoilType rocky = soilTypeRepository.findByNameIgnoreCase("Rocky").orElseThrow();

        List<Land> demos = List.of(
                demo("Premium agricultural land with canal access", "Baramati, Pune", "Pune",
                        18.1519, 74.5815, "6500000", 5.0, blackCotton, 35.0, 720, true, true),
                demo("Fertile riverside farm with year-round water", "Kolhapur", "Kolhapur",
                        16.7050, 74.2433, "7500000", 8.0, alluvial, 22.0, 1050, true, true),
                demo("Alphonso mango orchard land", "Devgad, Sindhudurg", "Sindhudurg",
                        16.3800, 73.3800, "9000000", 5.0, redLaterite, 40.0, 2800, false, true),
                demo("Highway-touch barren plot for investment", "Nashik", "Nashik",
                        19.9975, 73.7898, "2000000", 12.0, rocky, 180.0, 550, false, true),
                demo("Sugarcane-ready farm with drip irrigation", "Ahmednagar", "Ahmednagar",
                        19.0948, 74.7480, "12000000", 10.0, blackCotton, 28.0, 620, true, true));

        demos.forEach(land -> {
            scoringService.score(land);
            landRepository.save(land);
        });
        log.warn("Seeded {} DEMO listing(s). Disable earthscan.bootstrap.seed-demo-listings "
                + "before any real deployment.", demos.size());
    }

    private Land demo(String title, String location, String district,
                      double latitude, double longitude, String price, double acres,
                      SoilType soilType, double waterDepth, int rainfall,
                      boolean irrigation, boolean road) {
        Land land = new Land();
        land.setTitle(title);
        land.setDescription("Demonstration listing seeded for local development.");
        land.setLocation(location);
        land.setDistrict(district);
        land.setState("Maharashtra");
        land.setLatitude(latitude);
        land.setLongitude(longitude);
        land.setPrice(new BigDecimal(price));
        land.setSizeInAcres(acres);
        land.setSoilType(soilType);
        land.setGroundwaterLevelDepth(waterDepth);
        land.setAnnualRainfallMm(rainfall);
        land.setIrrigationAvailable(irrigation);
        land.setRoadAccess(road);
        // Owner id 1 is the bootstrap administrator seeded by auth-service.
        land.setOwnerId(1L);
        return land;
    }
}
