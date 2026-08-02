package com.earthscan.land.mapper;

import com.earthscan.land.domain.Land;
import com.earthscan.land.domain.SoilType;
import com.earthscan.land.dto.LandRequest;
import com.earthscan.land.dto.LandResponse;
import com.earthscan.land.dto.SoilTypeResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * Mapping between {@link Land} and its DTOs.
 *
 * <p>The request-to-entity direction is where this earns its place. The previous hand-written
 * {@code apply(request, land, soilType)} had to remember to <em>not</em> copy the fields a client
 * must never control — {@code ownerId}, both derived scores, {@code verified}, {@code status}. That
 * is a security property maintained by a developer remembering to leave lines out, which is exactly
 * the kind of thing that erodes over time.</p>
 *
 * <p>Here those fields are explicitly {@code ignore = true}. Combined with
 * {@code unmappedTargetPolicy=ERROR}, a new server-authoritative field added to the entity is a
 * <em>build failure</em> until someone states, in this file, whether the client may set it. The
 * decision becomes deliberate instead of accidental.</p>
 */
@Mapper
public interface LandMapper {

    @Mapping(target = "soilType", source = "soilType.name")
    @Mapping(target = "pricePerAcre", expression = "java(land.getPricePerAcre())")
    @Mapping(target = "status", expression = "java(land.getStatus().name())")
    LandResponse toResponse(Land land);

    SoilTypeResponse toSoilTypeResponse(SoilType soilType);

    /**
     * Applies a client request onto an entity.
     *
     * @param soilType resolved by the service from the request's soil-type name, so the mapper never
     *     performs a database lookup — mappers stay pure
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "soilType", source = "soilType")
    // Server-authoritative: never settable from a request body.
    @Mapping(target = "ownerId", ignore = true)
    @Mapping(target = "landIntelligenceScore", ignore = true)
    @Mapping(target = "borewellSuccessProbability", ignore = true)
    @Mapping(target = "scoredAt", ignore = true)
    @Mapping(target = "verified", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // Keeps an existing value when the incoming field is null, so a partial update does not blank
    // out columns the client simply did not mention.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyRequest(LandRequest request, SoilType soilType, @MappingTarget Land land);
}
