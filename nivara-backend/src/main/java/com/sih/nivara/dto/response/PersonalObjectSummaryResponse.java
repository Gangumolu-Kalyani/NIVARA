package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.ObjectCategory;

import java.util.UUID;

/**
 * Enough of an object to show what was involved in a memory. The full object DTO belongs to
 * the objects API when it is built.
 */
public record PersonalObjectSummaryResponse(
        UUID uuid,
        String name,
        ObjectCategory category) {
}
