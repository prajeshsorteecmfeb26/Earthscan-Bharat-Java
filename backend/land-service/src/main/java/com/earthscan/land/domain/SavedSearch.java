package com.earthscan.land.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A buyer's saved search or shortlisted property.
 *
 * <p>Replaces the browser-only {@code SavedSearchContext}, which kept this in {@code localStorage}.
 * That worked but meant a user's shortlist vanished when they cleared their browser or switched to
 * their phone — and it made the "alert me when a matching listing appears" feature impossible, since
 * the server had no idea what anyone was looking for. Persisting it server-side is what lets
 * notification-service match new listings against saved criteria.</p>
 */
@Entity
@Table(name = "saved_searches", indexes = {
        @Index(name = "idx_saved_searches_user", columnList = "user_id")
})
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Logical reference to the owning user in auth-service. See the note on {@code Land.ownerId}. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "location_query", length = 200)
    private String locationQuery;

    @Column(name = "soil_type_name", length = 60)
    private String soilTypeName;

    @Column(name = "min_price", precision = 18, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "max_price", precision = 18, scale = 2)
    private BigDecimal maxPrice;

    @Column(name = "min_score")
    private Double minScore;

    /** Set when the user shortlisted a specific listing rather than saving search criteria. */
    @Column(name = "land_id")
    private Long landId;

    @Column(name = "notify_on_match", nullable = false)
    private boolean notifyOnMatch = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SavedSearch() {
        // Required by JPA.
    }

    public SavedSearch(Long userId, String label) {
        this.userId = userId;
        this.label = label;
    }

    /** True when this row is a shortlisted listing rather than a set of search criteria. */
    public boolean isShortlistedListing() {
        return landId != null;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getLocationQuery() {
        return locationQuery;
    }

    public void setLocationQuery(String locationQuery) {
        this.locationQuery = locationQuery;
    }

    public String getSoilTypeName() {
        return soilTypeName;
    }

    public void setSoilTypeName(String soilTypeName) {
        this.soilTypeName = soilTypeName;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public Double getMinScore() {
        return minScore;
    }

    public void setMinScore(Double minScore) {
        this.minScore = minScore;
    }

    public Long getLandId() {
        return landId;
    }

    public void setLandId(Long landId) {
        this.landId = landId;
    }

    public boolean isNotifyOnMatch() {
        return notifyOnMatch;
    }

    public void setNotifyOnMatch(boolean notifyOnMatch) {
        this.notifyOnMatch = notifyOnMatch;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SavedSearch that)) {
            return false;
        }
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
