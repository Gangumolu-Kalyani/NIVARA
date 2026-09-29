package com.sih.nivara.game.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * Request to submit game results after playing a game.
 *
 * <p>This is a convenience wrapper that reuses {@link com.sih.nivara.dto.request.GameResultCreateRequest}
 * for the actual game result data. The gameCode and difficulty are passed here for convenience,
 * and the frontend can submit answers directly without needing to know the internal GameResultCreateRequest format.
 *
 * <p>The backend will use the gameCode to look up the game definition and validate difficulty.
 */
public record GameResultSubmitRequest(
        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z0-9_]+$")
        String gameCode,

        @NotNull
        @Min(1)
        Short difficulty,

        @NotNull
        @Pattern(regexp = "^(COMPLETED|ABANDONED)$")
        String status,

        @NotNull
        @PositiveOrZero
        Integer durationMs,

        @NotNull
        @PositiveOrZero
        Integer score,

        @PositiveOrZero
        Integer maxScore,

        @NotNull
        @PositiveOrZero
        Short totalQuestions,

        @NotNull
        @PositiveOrZero
        Short correctAnswers,

        @PositiveOrZero
        Short mistakes,

        @PositiveOrZero
        Short answerAttempts,

        @PositiveOrZero
        Short hintsUsed,

        @PositiveOrZero
        Integer avgReactionTimeMs,

        List<GameAnswerSubmit> answers
) {
}
