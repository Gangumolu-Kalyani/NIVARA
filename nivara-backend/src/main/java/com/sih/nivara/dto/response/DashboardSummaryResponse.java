package com.sih.nivara.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The caregiver dashboard for one patient's current day, in the patient's timezone.
 *
 * <p>careStatus is NEEDS_ATTENTION when any HIGH-severity alert is open or any of today's
 * reminders was missed or escalated, and ON_TRACK otherwise. It describes daily care only, never a
 * medical condition.
 */
public record DashboardSummaryResponse(
        PatientRef patient,
        CareStatus careStatus,
        ActivityCards dailyActivity,
        ReminderSummary reminderSummary,
        List<AlertResponse> openAlerts,
        Instant generatedAt) {

    public record PatientRef(UUID uuid, String fullName) {
    }

    public enum CareStatus {
        ON_TRACK,
        NEEDS_ATTENTION
    }

    /**
     * Today's reminder counts. pending is still waiting for an answer; missed includes escalated.
     * upcomingAppointments counts APPOINTMENT reminders due from now through the next seven days.
     */
    public record ReminderSummary(
            int totalToday,
            int completedToday,
            int pendingToday,
            int missedToday,
            int upcomingAppointments) {
    }
}
