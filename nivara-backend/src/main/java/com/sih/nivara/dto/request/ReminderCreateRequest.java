package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.DayOfWeekCode;
import com.sih.nivara.entity.enums.ReminderCategory;
import com.sih.nivara.entity.enums.ReminderRepeatType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Body of "add a reminder for this patient". The patient comes from the request path and the
 * creating account from the caller.
 *
 * <p>scheduledTime is a wall-clock time in the patient's timezone. repeatType defaults to DAILY,
 * escalateAfterMissed to 2 and active to true. A WEEKLY reminder needs repeatDays and a ONCE
 * reminder needs oneOffDate; the other types must leave them empty. That rule spans fields, so
 * ReminderService checks it (ck_reminders_weekly_rule and ck_reminders_once_rule back it up).
 */
public record ReminderCreateRequest(

        @NotNull
        ReminderCategory category,

        @NotBlank
        @Size(max = 150)
        String title,

        String instructions,

        @NotNull
        LocalTime scheduledTime,

        ReminderRepeatType repeatType,

        Set<DayOfWeekCode> repeatDays,

        LocalDate oneOffDate,

        @Min(1)
        @Max(10)
        Short escalateAfterMissed,

        Boolean active) {
}
