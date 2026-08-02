package com.earthscan.common.event;

import java.math.BigDecimal;

/** Published by land-service once a new listing has been scored and persisted. */
public class LandListedEvent extends IntegrationEvent {

    private Long landId;
    private String title;
    private String location;
    private Long ownerId;
    private BigDecimal price;
    private Double sizeInAcres;
    private Double landIntelligenceScore;
    private Double borewellSuccessProbability;

    public LandListedEvent() {
    }

    public Long getLandId() {
        return landId;
    }

    public void setLandId(Long landId) {
        this.landId = landId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
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

    public Double getLandIntelligenceScore() {
        return landIntelligenceScore;
    }

    public void setLandIntelligenceScore(Double landIntelligenceScore) {
        this.landIntelligenceScore = landIntelligenceScore;
    }

    public Double getBorewellSuccessProbability() {
        return borewellSuccessProbability;
    }

    public void setBorewellSuccessProbability(Double borewellSuccessProbability) {
        this.borewellSuccessProbability = borewellSuccessProbability;
    }
}
