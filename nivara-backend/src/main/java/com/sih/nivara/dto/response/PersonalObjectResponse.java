package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.ObjectCategory;

import java.time.Instant;
import java.util.UUID;

/**
 * An everyday object the patient uses, as the objects API returns it. Memories embed the
 * shorter {@link PersonalObjectSummaryResponse} instead.
 *
 * <p>Identified by uuid. The bigint primary key, the optimistic-locking version, deleted_at
 * and the creating account stay inside the backend.
 */
public record PersonalObjectResponse(
        UUID uuid,
        UUID patientUuid,
        String name,
        ObjectCategory category,
        String usualLocation,
        String description,
        boolean includeInGames,
        Instant createdAt,
        Instant updatedAt) {
}
