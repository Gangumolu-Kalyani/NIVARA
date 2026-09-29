package com.sih.nivara.game.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * A personalized game instance ready to be played by a patient.
 *
 * <p>This is the response from the game generation endpoint. It contains all the questions,
 * hints, and references to patient data needed for the frontend to render and validate the game.
 *
 * <p>Game instances are transient - they are not stored in the database. Only {@link com.sih.nivara.entity.GameResult}
 * records (game attempts) are persisted.
 */
public record GeneratedGameResponse(
        UUID gameInstanceId,
        String gameCode,
        String gameName,
        short difficulty,
        List<GeneratedQuestion> questions,
        List<GameContentReference> contentReferences,
        GameMetadata metadata
) {
}
