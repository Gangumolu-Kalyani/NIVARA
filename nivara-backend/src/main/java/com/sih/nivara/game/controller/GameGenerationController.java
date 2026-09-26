package com.sih.nivara.game.controller;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.game.dto.request.GenerateGameRequest;
import com.sih.nivara.game.dto.response.AvailableGamesResponse;
import com.sih.nivara.game.dto.response.GeneratedGameResponse;
import com.sih.nivara.game.service.GameGenerator;
import com.sih.nivara.game.service.GameGeneratorFactory;
import com.sih.nivara.service.GameService;
import com.sih.nivara.service.PatientAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API for generating personalized cognitive games from patient data.
 *
 * <p>Game instances are transient - they are generated on-demand from the patient's
 * people, places, objects, and memories, and returned to the frontend for display.
 * Only {@link com.sih.nivara.entity.GameResult} records (game attempts) are persisted.
 *
 * <p>All endpoints require a bearer token and are authorized by {@link PatientAccessService}
 * against patient_caregivers. GENERATE requires EDITOR access, while AVAILABLE and LIST
 * require VIEWER access.
 */
@RestController
@RequestMapping("/api/patients/{patientUuid}/games")
public class GameGenerationController {

    private final GameGeneratorFactory generatorFactory;
    private final GameService gameService;
    private final PatientAccessService patientAccessService;

    public GameGenerationController(GameGeneratorFactory generatorFactory,
                                    GameService gameService,
                                    PatientAccessService patientAccessService) {
        this.generatorFactory = generatorFactory;
        this.gameService = gameService;
        this.patientAccessService = patientAccessService;
    }

    /**
     * Generates a personalized game instance for the patient.
     *
     * <p>The game is created from the patient's existing data (people, places, objects, memories)
     * based on the game definition, requested difficulty, and language preference.
     *
     * <p>Response includes all questions, hints, and content references needed for the frontend
     * to render the game without additional lookups.
     */
    @PostMapping("/generate")
    public ResponseEntity<GeneratedGameResponse> generateGame(
            @PathVariable UUID patientUuid,
            @Valid @RequestBody GenerateGameRequest request) {

        // Authorize and get patient
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);

        // Validate game code exists
        var gameOptional = gameService.findByCode(request.gameCode());
        if (gameOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No game with code " + request.gameCode());
        }
        var game = gameOptional.get();

        // Validate difficulty is within game's allowed range
        if (request.difficulty() < game.getMinDifficulty() ||
                request.difficulty() > game.getMaxDifficulty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Difficulty " + request.difficulty() + " is outside the allowed range " +
                            game.getMinDifficulty() + ".." + game.getMaxDifficulty() +
                            " for game " + game.getCode());
        }

        // Get the appropriate generator
        var generatorOptional = generatorFactory.getGenerator(game.getCode());
        if (generatorOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "Game generation is not implemented for " + game.getCode());
        }
        var generator = generatorOptional.get();

        // Check if patient has enough content
        if (!generator.canGenerate(patient, request.difficulty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Patient does not have enough content to play " + game.getName() +
                            " at difficulty " + request.difficulty());
        }

        // Generate the game
        var generatedGame = generator.generate(patient, request, game);

        // Return with 201 Created
        URI location = URI.create("/api/patients/" + patientUuid +
                "/game-instances/" + generatedGame.gameInstanceId());
        return ResponseEntity.created(location).body(generatedGame);
    }

    /**
     * Lists games available for this patient (with content availability status).
     *
     * <p>Returns all active games, indicating whether the patient has enough content
     * to play each game at various difficulty levels.
     */
    @GetMapping("/available")
    public List<AvailableGamesResponse> getAvailableGames(@PathVariable UUID patientUuid) {
        // Authorize and get patient
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);

        // Get all active games
        var games = gameService.findActive();

        return games.stream()
                .map(game -> {
                    var generatorOptional = generatorFactory.getGenerator(game.getCode());
                    if (generatorOptional.isEmpty()) {
                        // Game exists but no generator implemented yet
                        return new AvailableGamesResponse(
                                game.getCode(),
                                game.getName(),
                                game.getCognitiveDomain(),
                                false,
                                "Generation not implemented",
                                game.getMinDifficulty(),
                                game.getMaxDifficulty()
                        );
                    }

                    var generator = generatorOptional.get();

                    // Check availability at each difficulty level (1-5)
                    boolean playable = false;
                    String missingContent = null;

                    for (short diff = game.getMinDifficulty(); diff <= game.getMaxDifficulty(); diff++) {
                        if (generator.canGenerate(patient, diff)) {
                            playable = true;
                            break;
                        }
                        // Store the reason for the first unplayable level
                        if (missingContent == null) {
                            missingContent = getMissingContentReason(patient, game, generator, diff);
                        }
                    }

                    return new AvailableGamesResponse(
                            game.getCode(),
                            game.getName(),
                            game.getCognitiveDomain(),
                            playable,
                            playable ? null : missingContent,
                            game.getMinDifficulty(),
                            game.getMaxDifficulty()
                    );
                })
                .toList();
    }

    /**
     * Gets a human-readable reason why the patient can't play the game at the given difficulty.
     */
    private String getMissingContentReason(Patient patient, com.sih.nivara.entity.Game game,
                                           GameGenerator generator, short difficulty) {
        // This is a placeholder - implementations can provide more specific reasons
        // For now, use a generic message
        return "Insufficient personalized content for difficulty " + difficulty;
    }
}
