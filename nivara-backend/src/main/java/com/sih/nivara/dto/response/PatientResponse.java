package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.CognitiveStage;

import java.time.Instant;
import java.util.UUID;

/**
 * A patient profile as the API returns it.
 *
 * <p>The patient is identified by uuid, the stable public identifier the schema provides
 * (uq_patients_uuid). The bigint primary key, the optimistic-locking version, deleted_at
 * and the owning account rows stay inside the backend.
 */
public record PatientResponse(
        UUID uuid,
        String fullName,
        String preferredName,
        Short birthYear,
        CognitiveStage cognitiveStage,
        String preferredLanguage,
        String timezone,
        Instant createdAt,
        Instant updatedAt) {
}
