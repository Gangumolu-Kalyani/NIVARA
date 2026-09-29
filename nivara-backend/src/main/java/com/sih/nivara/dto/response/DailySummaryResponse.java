package com.sih.nivara.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * A plain-language summary of one of the patient's days.
 *
 * <p>careRhythm rates each part of the day from the reminders that had fallen due in it: STRONG
 * when at least 80% were completed with little prompting, NEEDS_PROMPTING when at least half
 * were, LOW otherwise, and NO_DATA when none had fallen due. Morning is before 12:00, afternoon
 * 12:00 to 17:00 and evening from 17:00, in the patient's timezone. patterns are observations over
 * the seven days ending on this date.
 */
public record DailySummaryResponse(
        LocalDate date,
        ActivityCards dailyActivity,
        int upcomingAppointments,
        List<String> summaryLines,
        CareRhythm careRhythm,
        List<String> patterns) {

    public record CareRhythm(CareRhythmLevel morning, CareRhythmLevel afternoon, CareRhythmLevel evening) {
    }

    public enum CareRhythmLevel {
        STRONG,
        NEEDS_PROMPTING,
        LOW,
        NO_DATA
    }
}
