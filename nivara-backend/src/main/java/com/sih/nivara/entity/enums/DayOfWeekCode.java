package com.sih.nivara.entity.enums;

import java.time.DayOfWeek;

/**
 * One entry of reminders.repeat_days - CHECK ck_reminders_repeat_days_format (V5). The column
 * holds several of these, comma-separated; see {@link com.sih.nivara.entity.DayOfWeekCodesConverter}.
 */
public enum DayOfWeekCode {
    MON(DayOfWeek.MONDAY),
    TUE(DayOfWeek.TUESDAY),
    WED(DayOfWeek.WEDNESDAY),
    THU(DayOfWeek.THURSDAY),
    FRI(DayOfWeek.FRIDAY),
    SAT(DayOfWeek.SATURDAY),
    SUN(DayOfWeek.SUNDAY);

    private final DayOfWeek dayOfWeek;

    DayOfWeekCode(DayOfWeek dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public DayOfWeek toDayOfWeek() {
        return dayOfWeek;
    }
}
