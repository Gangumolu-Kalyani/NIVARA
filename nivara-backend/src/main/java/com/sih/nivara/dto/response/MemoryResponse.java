package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.MemorySource;
import com.sih.nivara.entity.enums.MemoryType;
import com.sih.nivara.entity.enums.TimeOfDay;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A memory as the API returns it, with the people, place and objects resolved to summaries
 * rather than bare identifiers, so a client can render it without further calls.
 *
 * <p>Identified by uuid, like every other resource here. The bigint primary key, the
 * optimistic-locking version, deleted_at and the recording account stay inside the backend.
 *
 * <p>place is null when the memory has none; people and objects are empty lists, never null.
 */
public record MemoryResponse(
        UUID uuid,
        UUID patientUuid,
        String title,
        String description,
        MemoryType memoryType,
        LocalDate occurredOn,
        TimeOfDay timeOfDay,
        PlaceSummaryResponse place,
        String languageCode,
        MemorySource source,
        boolean includeInGames,
        List<PersonSummaryResponse> people,
        List<PersonalObjectSummaryResponse> objects,
        Instant createdAt,
        Instant updatedAt) {
}
