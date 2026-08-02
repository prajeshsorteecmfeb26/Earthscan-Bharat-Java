package com.earthscan.land.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;

/**
 * A soil classification, with the agronomic figures the scoring engine needs.
 *
 * <p>This table is the single biggest schema improvement over the original design. There,
 * {@code Land.SoilType} was free text and the scoring logic read
 * {@code land.SoilType.Contains("Black")}. Three problems followed from that: "Black Cotton",
 * "Deep Black", "Medium Black" and "Shallow Black" all scored identically despite very different
 * water retention; a typo like "Balck Cotton" silently scored as unknown; and the numbers driving
 * the score were buried in Java rather than being data an agronomist could correct.</p>
 *
 * <p>Promoting soil to its own table puts {@code fertilityIndex} and {@code waterRetentionIndex}
 * where they belong — as facts about the soil, stored once, editable without a redeploy.</p>
 */
@Entity
@Table(name = "soil_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_soil_types_name", columnNames = "name"))
public class SoilType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    /** 0–100. How productive the soil is for typical Maharashtra cropping patterns. */
    @Column(name = "fertility_index", nullable = false)
    private int fertilityIndex;

    /** 0–100. How well the soil holds moisture between irrigation cycles. */
    @Column(name = "water_retention_index", nullable = false)
    private int waterRetentionIndex;

    @Column(name = "description", length = 255)
    private String description;

    protected SoilType() {
        // Required by JPA.
    }

    public SoilType(String name, int fertilityIndex, int waterRetentionIndex, String description) {
        this.name = name;
        this.fertilityIndex = fertilityIndex;
        this.waterRetentionIndex = waterRetentionIndex;
        this.description = description;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFertilityIndex() {
        return fertilityIndex;
    }

    public void setFertilityIndex(int fertilityIndex) {
        this.fertilityIndex = fertilityIndex;
    }

    public int getWaterRetentionIndex() {
        return waterRetentionIndex;
    }

    public void setWaterRetentionIndex(int waterRetentionIndex) {
        this.waterRetentionIndex = waterRetentionIndex;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SoilType soilType)) {
            return false;
        }
        return Objects.equals(name, soilType.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "SoilType{" + name + "}";
    }
}
