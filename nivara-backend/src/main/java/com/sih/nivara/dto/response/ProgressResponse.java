package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.CognitiveDomain;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Cognitive-game performance over the last rangeDays calendar days (today included) in the
 * patient's timezone.
 *
 * <p>Accuracy figures are percentages rounded to one decimal, averaged over COMPLETED attempts
 * only, and null when there are none. difficultyDistribution counts every attempt by difficulty
 * level, keyed "1" to "5". accuracyTrend has one point per day, oldest first; recentActivities
 * holds up to ten attempts, newest first.
 */
public record ProgressResponse(
        int rangeDays,
        int gamesCompleted,
        int gamesAbandoned,
        Double averageAccuracy,
        Integer averageReactionTimeMs,
        Map<String, Integer> difficultyDistribution,
        Map<CognitiveDomain, Double> domainAccuracy,
        PerformanceTrend overallAccuracyTrend,
        List<DailyAccuracyPoint> accuracyTrend,
        List<GameResultSummaryResponse> recentActivities) {

    /**
     * Compares the average accuracy of the second half of the range with the first half. Needs at
     * least two completed attempts in each half; a change of more than five points is UP or DOWN.
     */
    public enum PerformanceTrend {
        UP,
        DOWN,
        STABLE,
        INSUFFICIENT_DATA
    }

    public record DailyAccuracyPoint(LocalDate date, Double averageAccuracy, int gamesPlayed) {
    }
}
