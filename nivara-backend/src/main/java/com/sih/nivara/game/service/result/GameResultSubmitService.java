package com.sih.nivara.game.service.result;

import com.sih.nivara.dto.mapper.GameResultMapper;
import com.sih.nivara.dto.request.GameResultCreateRequest;
import com.sih.nivara.dto.request.GameResultAnswerRequest;
import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.GameResult;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.GameResultStatus;
import com.sih.nivara.game.dto.request.GameAnswerSubmit;
import com.sih.nivara.game.dto.request.GameResultSubmitRequest;
import com.sih.nivara.game.service.GameGeneratorFactory;
import com.sih.nivara.service.GameResultService;
import com.sih.nivara.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for submitting game results after playing a game.
 *
 * <p>This service converts {@link GameResultSubmitRequest} to {@link GameResultCreateRequest}
 * and delegates to {@link GameResultService} for actual result recording.
 */
@Service
@Transactional
public class GameResultSubmitService {

    private final GameResultService gameResultService;
    private final GameService gameService;
    private final GameGeneratorFactory generatorFactory;

    public GameResultSubmitService(GameResultService gameResultService,
                                   GameService gameService,
                                   GameGeneratorFactory generatorFactory) {
        this.gameResultService = gameResultService;
        this.gameService = gameService;
        this.generatorFactory = generatorFactory;
    }

    /**
     * Submits game results for a patient after they complete a game.
     *
     * <p>This converts the simplified frontend request format to the internal
     * GameResultCreateRequest and delegates to GameResultService.
     */
    @Transactional
    public GameResult submitResult(Patient patient, GameResultSubmitRequest request) {
        // Validate game exists
        var gameOptional = gameService.findByCode(request.gameCode());
        if (gameOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No game with code " + request.gameCode());
        }
        var game = gameOptional.get();

        // Validate difficulty is within allowed range
        if (request.difficulty() < game.getMinDifficulty() ||
                request.difficulty() > game.getMaxDifficulty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Difficulty " + request.difficulty() + " is outside the allowed range " +
                            game.getMinDifficulty() + ".." + game.getMaxDifficulty());
        }

        // Convert to GameResultCreateRequest
        var createRequest = convertToCreateRequest(request, patient, game);

        // Submit using existing GameResultService
        var result = gameResultService.record(patient, createRequest);

        return result.result();
    }

    /**
     * Converts GameResultSubmitRequest to GameResultCreateRequest.
     */
    private GameResultCreateRequest convertToCreateRequest(GameResultSubmitRequest submitRequest,
                                                           Patient patient,
                                                           Game game) {
        var status = GameResultStatus.valueOf(submitRequest.status());

        var startedAt = getStartedAt(submitRequest);

        Instant completedAt = null;
        if (status == GameResultStatus.COMPLETED) {
            completedAt = startedAt.plusMillis(submitRequest.durationMs());
        }

        // Convert answers
        List<GameResultAnswerRequest> answers = null;
        if (submitRequest.answers() != null) {
            answers = submitRequest.answers().stream()
                    .map(this::convertAnswer)
                    .toList();
        }

        return new GameResultCreateRequest(
                null,  // uuid - let backend generate
                submitRequest.gameCode(),
                submitRequest.difficulty(),
                status,
                startedAt,
                completedAt,
                submitRequest.durationMs(),
                submitRequest.score(),
                submitRequest.maxScore(),
                submitRequest.totalQuestions(),
                submitRequest.correctAnswers(),
                submitRequest.mistakes(),
                submitRequest.answerAttempts(),
                submitRequest.hintsUsed(),
                submitRequest.avgReactionTimeMs(),
                null,  // playedOffline - default false
                null,  // languageCode - use patient's default
                answers
        );
    }

    /**
     * Gets the startedAt time.
     * In a real implementation, this would come from the frontend or be stored in the game instance.
     * For now, we use current time.
     */
    private Instant getStartedAt(GameResultSubmitRequest request) {
        // In production, this should come from the frontend (when game started)
        // or be stored when generating the game instance
        return Instant.now();
    }

    /**
     * Converts GameAnswerSubmit to GameResultAnswerRequest.
     */
    private GameResultAnswerRequest convertAnswer(GameAnswerSubmit answer) {
        return new GameResultAnswerRequest(
                answer.questionNumber(),
                answer.questionType(),
                answer.memoryUuid(),
                answer.personUuid(),
                answer.placeUuid(),
                answer.objectUuid(),
                answer.correct(),
                answer.attempts(),
                answer.reactionTimeMs(),
                answer.hintUsed(),
                answer.answeredAt()
        );
    }

    /**
     * Records a game result directly (bypassing the submit request conversion).
     * Useful for testing or when the frontend already has the correct format.
     */
    @Transactional
    public GameResult recordResult(Patient patient, GameResultCreateRequest request) {
        var result = gameResultService.record(patient, request);
        return result.result();
    }
}
