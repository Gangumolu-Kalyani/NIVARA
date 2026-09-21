package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.PlaceType;

import java.time.Instant;
import java.util.UUID;

/**
 * A place that matters to the patient, as the places API returns it. Memories embed the
 * shorter {@link PlaceSummaryResponse} instead.
 *
 * <p>Identified by uuid. The bigint primary key, the optimistic-locking version, deleted_at
 * and the creating account stay inside the backend.
 */
public record PlaceResponse(
        UUID uuid,
        UUID patientUuid,
        String name,
        PlaceType placeType,
        String description,
        boolean includeInGames,
        Instant createdAt,
        Instant updatedAt) {
}
