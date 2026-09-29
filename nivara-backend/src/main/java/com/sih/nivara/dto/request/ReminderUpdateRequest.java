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
 * Body of "change a reminder": a full replacement, so every NOT NULL column is required and an
 * omitted instructions clears it. The same repeat rules as {@link ReminderCreateRequest} apply.
 */
public record ReminderUpdateRequest(

        @NotNull
        ReminderCategory category,

        @NotBlank
        @Size(max = 150)
        String title,

        String instructions,

        @NotNull
        LocalTime scheduledTime,

        @NotNull
        ReminderRepeatType repeatType,

        Set<DayOfWeekCode> repeatDays,

        LocalDate oneOffDate,

        @NotNull
        @Min(1)
        @Max(10)
        Short escalateAfterMissed,

        @NotNull
        Boolean active) {
}
