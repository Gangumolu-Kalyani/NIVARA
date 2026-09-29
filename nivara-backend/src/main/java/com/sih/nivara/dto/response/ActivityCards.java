package com.sih.nivara.dto.response;

/**
 * The four daily-activity cards on the dashboard: how many of the day's reminders in each
 * category were completed, out of how many were scheduled.
 */
public record ActivityCards(
        ActivityCard cognitiveActivity,
        ActivityCard medicine,
        ActivityCard hydration,
        ActivityCard movement) {

    public record ActivityCard(int completed, int total) {
    }
}
