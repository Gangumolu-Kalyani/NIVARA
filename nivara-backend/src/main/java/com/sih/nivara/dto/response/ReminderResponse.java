package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.DayOfWeekCode;
import com.sih.nivara.entity.enums.ReminderCategory;
import com.sih.nivara.entity.enums.ReminderRepeatType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A reminder as the API returns it. scheduledTime is always "HH:mm:ss", in the patient's
 * timezone. repeatDays is empty, never null, unless the reminder is WEEKLY.
 */
public record ReminderResponse(
        UUID uuid,
        UUID patientUuid,
        ReminderCategory category,
        String title,
        String instructions,
        String scheduledTime,
        ReminderRepeatType repeatType,
        List<DayOfWeekCode> repeatDays,
        LocalDate oneOffDate,
        short escalateAfterMissed,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
