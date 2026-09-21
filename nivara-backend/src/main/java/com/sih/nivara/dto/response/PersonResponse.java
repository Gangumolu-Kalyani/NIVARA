package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.RelationshipType;

import java.time.Instant;
import java.util.UUID;

/**
 * A person the patient knows, as the people API returns it. Memories embed the shorter
 * {@link PersonSummaryResponse} instead.
 *
 * <p>Identified by uuid. The bigint primary key, the optimistic-locking version, deleted_at
 * and the creating account stay inside the backend.
 */
public record PersonResponse(
        UUID uuid,
        UUID patientUuid,
        String fullName,
        String calledAs,
        RelationshipType relationship,
        String relationshipLabel,
        String description,
        boolean includeInGames,
        Instant createdAt,
        Instant updatedAt) {
}
