package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.GameContentSource;
import com.sih.nivara.entity.enums.PersonalizationType;

/**
 * A game from the catalog. Identified by code, the immutable public key of table games (V3);
 * the table has no uuid, and the bigint primary key stays inside the backend.
 *
 * <p>personalizationType and minContentItems are null for standard games, which draw on no
 * patient data.
 */
public record GameResponse(
        String code,
        String name,
        String description,
        CognitiveDomain cognitiveDomain,
        GameContentSource contentSource,
        PersonalizationType personalizationType,
        Short minContentItems,
        short minDifficulty,
        short maxDifficulty,
        boolean active) {
}
