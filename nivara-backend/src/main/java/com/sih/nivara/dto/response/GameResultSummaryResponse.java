package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.GameResultStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt in a patient's history, without its answers so the list stays a single light
 * query. The same attempt with answers is {@link GameResultResponse}.
 *
 * <p>cognitiveDomain is the snapshot taken from the game when the attempt was recorded, and
 * accuracy is computed by the database; it is null when no question was asked.
 */
public record GameResultSummaryResponse(
        UUID uuid,
        UUID patientUuid,
        GameSummaryResponse game,
        CognitiveDomain cognitiveDomain,
        short difficultyLevel,
        GameResultStatus status,
        Instant startedAt,
        Instant completedAt,
        int durationMs,
        int score,
        Integer maxScore,
        short totalQuestions,
        short correctAnswers,
        short mistakes,
        short answerAttempts,
        short hintsUsed,
        Integer avgReactionTimeMs,
        BigDecimal accuracy,
        boolean playedOffline,
        String languageCode,
        Instant createdAt) {
}
