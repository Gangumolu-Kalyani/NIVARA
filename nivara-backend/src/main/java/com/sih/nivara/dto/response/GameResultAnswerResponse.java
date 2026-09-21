package com.sih.nivara.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One question of an attempt, as the API returns it. Answers have no public identifier of their
 * own: questionNumber is unique within the attempt (uq_game_result_answers_question), so the
 * attempt uuid plus questionNumber names one.
 *
 * <p>At most one of the four subject uuids is set: the memory, person, place or object the
 * question asked about.
 */
public record GameResultAnswerResponse(
        short questionNumber,
        String questionType,
        UUID memoryUuid,
        UUID personUuid,
        UUID placeUuid,
        UUID objectUuid,
        boolean correct,
        short attempts,
        Integer reactionTimeMs,
        boolean hintUsed,
        Instant answeredAt) {
}
