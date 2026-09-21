package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.CognitiveDomain;
import com.sih.nivara.entity.enums.GameResultStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One attempt with its answers, ordered by question number. The history list returns
 * {@link GameResultSummaryResponse} instead, which has the same fields without answers.
 *
 * <p>Identified by uuid. The bigint primary key never leaves the backend; answers are named by
 * questionNumber within the attempt.
 */
public record GameResultResponse(
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
        Instant createdAt,
        List<GameResultAnswerResponse> answers) {
}
