package com.sih.nivara.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * One question or round inside an uploaded attempt, mirroring table game_result_answers (V3).
 *
 * <p>The item the question asked about is named by at most one of the four subject uuids;
 * ck_game_result_answers_single_subject forbids more, and the service rejects that before the
 * database has to. Each subject must belong to the same patient as the attempt.
 *
 * <p>questionType is free text because the column has no CHECK list: the games module can add
 * question types without a migration. Omitted attempts and hintUsed keep the column defaults
 * of 1 and false.
 */
public record GameResultAnswerRequest(

        @NotNull
        @Min(1)
        Short questionNumber,

        @NotBlank
        @Size(max = 40)
        String questionType,

        UUID memoryUuid,

        UUID personUuid,

        UUID placeUuid,

        UUID objectUuid,

        @NotNull
        Boolean correct,

        @Min(1)
        Short attempts,

        @PositiveOrZero
        Integer reactionTimeMs,

        Boolean hintUsed,

        Instant answeredAt) {
}
