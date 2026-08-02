package com.earthscan.land.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A land listing.
 *
 * <p><strong>On {@code ownerId} being a plain {@code Long} and not a JPA relationship:</strong> the
 * owning user lives in a different service with its own MySQL schema, so a foreign key is not
 * available and would not be desirable — a database-level constraint across two services'
 * schemas re-couples them at exactly the layer microservices are meant to separate. Referential
 * integrity is instead maintained by consuming {@code user.deleted} from RabbitMQ. The trade-off is
 * a window during which this table can reference a user that no longer exists; that window is the
 * price of the decoupling, and it is why the delete consumer must be idempotent.</p>
 */
@Entity
@Table(name = "lands", indexes = {
        @Index(name = "idx_lands_owner", columnList = "owner_id"),
        @Index(name = "idx_lands_district", columnList = "district"),
        @Index(name = "idx_lands_status", columnList = "status"),
        // Search filters on price and score together, so a composite index serves the common query.
        @Index(name = "idx_lands_price_score", columnList = "price,land_intelligence_score")
})
public class Land {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "location", nullable = false, length = 200)
    private String location;

    @Column(name = "district", length = 80)
    private String district;

    @Column(name = "state", length = 80)
    private String state = "Maharashtra";

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /**
     * {@code DECIMAL(18,2)}, never {@code double}. Land prices here run to crores, and binary
     * floating point cannot represent those exactly — a rounding drift of a few paise per row is
     * unacceptable on a figure someone makes a purchase decision from.
     */
    @Column(name = "price", nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "size_in_acres", nullable = false)
    private Double sizeInAcres;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "soil_type_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lands_soil_type"))
    private SoilType soilType;

    /** Metres below ground level at which water is reliably found. */
    @Column(name = "groundwater_level_depth", nullable = false)
    private Double groundwaterLevelDepth;

    /** Long-term average annual rainfall in millimetres, used by the scoring engine. */
    @Column(name = "annual_rainfall_mm")
    private Integer annualRainfallMm;

    @Column(name = "irrigation_available", nullable = false)
    private boolean irrigationAvailable;

    @Column(name = "road_access", nullable = false)
    private boolean roadAccess;

    // ---------------------------------------------------------------- derived by the scoring engine
    @Column(name = "land_intelligence_score", nullable = false)
    private Double landIntelligenceScore = 0d;

    @Column(name = "borewell_success_probability", nullable = false)
    private Double borewellSuccessProbability = 0d;

    @Column(name = "scored_at")
    private Instant scoredAt;

    // ---------------------------------------------------------------- ownership and lifecycle
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Land() {
        // Required by JPA and service instantiation.
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Applies a freshly computed score. Called only by the scoring service. */
    public void applyScores(double intelligenceScore, double borewellProbability) {
        this.landIntelligenceScore = intelligenceScore;
        this.borewellSuccessProbability = borewellProbability;
        this.scoredAt = Instant.now();
    }

    /** Total price divided by area, the figure buyers actually compare listings on. */
    public BigDecimal getPricePerAcre() {
        if (price == null || sizeInAcres == null || sizeInAcres <= 0) {
            return BigDecimal.ZERO;
        }
        return price.divide(BigDecimal.valueOf(sizeInAcres), 2, java.math.RoundingMode.HALF_UP);
    }

    public boolean isOwnedBy(Long userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Double getSizeInAcres() {
        return sizeInAcres;
    }

    public void setSizeInAcres(Double sizeInAcres) {
        this.sizeInAcres = sizeInAcres;
    }

    public SoilType getSoilType() {
        return soilType;
    }

    public void setSoilType(SoilType soilType) {
        this.soilType = soilType;
    }

    public Double getGroundwaterLevelDepth() {
        return groundwaterLevelDepth;
    }

    public void setGroundwaterLevelDepth(Double groundwaterLevelDepth) {
        this.groundwaterLevelDepth = groundwaterLevelDepth;
    }

    public Integer getAnnualRainfallMm() {
        return annualRainfallMm;
    }

    public void setAnnualRainfallMm(Integer annualRainfallMm) {
        this.annualRainfallMm = annualRainfallMm;
    }

    public boolean isIrrigationAvailable() {
        return irrigationAvailable;
    }

    public void setIrrigationAvailable(boolean irrigationAvailable) {
        this.irrigationAvailable = irrigationAvailable;
    }

    public boolean isRoadAccess() {
        return roadAccess;
    }

    public void setRoadAccess(boolean roadAccess) {
        this.roadAccess = roadAccess;
    }

    public Double getLandIntelligenceScore() {
        return landIntelligenceScore;
    }

    public Double getBorewellSuccessProbability() {
        return borewellSuccessProbability;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public void setStatus(ListingStatus status) {
        this.status = status;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Land land)) {
            return false;
        }
        return id != null && Objects.equals(id, land.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Land{id=" + id + ", title='" + title + "', ownerId=" + ownerId + "}";
    }
}
