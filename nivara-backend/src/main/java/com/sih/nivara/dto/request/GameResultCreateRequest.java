package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.GameResultStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Body of "record one attempt at one game", with its answers, for the patient in the path.
 *
 * <p>Bounds mirror table game_results (V3). Rules that span fields or need the games row, such
 * as difficulty inside the game's own range or correctAnswers not exceeding totalQuestions,
 * are checked by the service, so a violation is a 400 rather than a database error.
 *
 * <p>The client never sends the cognitive domain, which the backend snapshots from the game,
 * nor accuracy, which the database computes. uuid is optional: a device that generates it can
 * replay an offline upload safely, because a second upload of the same uuid returns the stored
 * attempt instead of creating another.
 *
 * <p>Omitted counters keep the column defaults: mistakes, answerAttempts and hintsUsed of 0,
 * playedOffline of false.
 */
public record GameResultCreateRequest(

        UUID uuid,

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[A-Z0-9_]+$")
        String gameCode,

        @NotNull
        @Min(1)
        @Max(5)
        Short difficultyLevel,

        @NotNull
        GameResultStatus status,

        @NotNull
        @PastOrPresent
        Instant startedAt,

        Instant completedAt,

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

        Boolean playedOffline,

        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String languageCode,

        List<@Valid @NotNull GameResultAnswerRequest> answers) {
}
