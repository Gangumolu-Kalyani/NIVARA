package com.sih.nivara.game.dto.response;

import com.sih.nivara.entity.enums.CognitiveDomain;

/**
 * Information about a game and whether it's playable for the current patient.
 */
public record AvailableGamesResponse(
        String gameCode,
        String gameName,
        CognitiveDomain domain,
        boolean playable,
        String missingContent,
        short minDifficulty,
        short maxDifficulty
) {
}
