package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.PlaceType;

import java.util.UUID;

/**
 * Enough of a place to show where a memory happened. The full place DTO belongs to the
 * places API when it is built.
 */
public record PlaceSummaryResponse(
        UUID uuid,
        String name,
        PlaceType placeType) {
}
