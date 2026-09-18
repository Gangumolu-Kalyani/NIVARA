package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.RelationshipType;

import java.util.UUID;

/**
 * Enough of a person to show who was involved in a memory, without carrying that person's
 * whole record. The full person DTO belongs to the people API when it is built.
 */
public record PersonSummaryResponse(
        UUID uuid,
        String fullName,
        String calledAs,
        RelationshipType relationship) {
}
