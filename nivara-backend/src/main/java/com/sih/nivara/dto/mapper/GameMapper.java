package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.response.GameResponse;
import com.sih.nivara.dto.response.GameSummaryResponse;
import com.sih.nivara.entity.Game;

/**
 * Explicit conversion from {@link Game} to its DTOs. Read-only: the catalog is reference data
 * maintained by migrations, so there is no request side.
 */
public final class GameMapper {

    private GameMapper() {
        // utility class
    }

    public static GameResponse toResponse(Game game) {
        return new GameResponse(
                game.getCode(),
                game.getName(),
                game.getDescription(),
                game.getCognitiveDomain(),
                game.getContentSource(),
                game.getPersonalizationType(),
                game.getMinContentItems(),
                game.getMinDifficulty(),
                game.getMaxDifficulty(),
                game.isActive());
    }

    /** The short form a game result embeds. */
    public static GameSummaryResponse toSummary(Game game) {
        return new GameSummaryResponse(game.getCode(), game.getName());
    }
}
