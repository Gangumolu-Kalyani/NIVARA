package com.sih.nivara.game.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * One answer submitted after playing a game.
 */
public record GameAnswerSubmit(
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

        Instant answeredAt
) {
}
