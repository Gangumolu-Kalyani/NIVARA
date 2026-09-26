package com.sih.nivara.game.service;

import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.game.dto.request.GenerateGameRequest;
import com.sih.nivara.game.dto.response.GeneratedGameResponse;

/**
 * Interface for generating personalized game instances from patient data.
 *
 * <p>Each game implementation (Memory Match, Memory Timeline, etc.) implements this interface
 * to define how it generates questions from the patient's people, places, objects, and memories.
 */
public interface GameGenerator {

    /**
     * Generates a game instance for the given patient.
     *
     * @param patient      the patient to generate the game for
     * @param request      the generation request (game code, difficulty, language, etc.)
     * @param game         the game definition from the catalog
     * @return a GeneratedGameResponse with questions, hints, and content references
     */
    GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game);

    /**
     * Returns the game code that this generator handles.
     *
     * @return the game code (e.g., "MEMORY_MATCH", "FACE_NAME_MATCH")
     */
    String getGameCode();

    /**
     * Checks if the patient has enough content to play this game at the requested difficulty.
     *
     * @param patient      the patient
     * @param difficulty   the requested difficulty level
     * @return true if the patient has enough content, false otherwise
     */
    boolean canGenerate(Patient patient, short difficulty);
}
