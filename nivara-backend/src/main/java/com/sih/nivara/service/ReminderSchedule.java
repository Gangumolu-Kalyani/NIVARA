package com.sih.nivara.service;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.enums.DayOfWeekCode;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Calendar rules for reminders: which days a reminder falls on, and the instant it falls due.
 *
 * <p>A reminder's scheduledTime is wall-clock time in the patient's timezone (patients.timezone),
 * so the same reminder is due at 08:00 local time every day regardless of where the server runs.
 * Pure functions only: no repository, no clock.
 */
public final class ReminderSchedule {

    /** Used when a patient's stored timezone is not a valid zone id. */
    static final ZoneId FALLBACK_ZONE = ZoneId.of("Asia/Kolkata");

    private ReminderSchedule() {
        // utility class
    }

    /** The patient's timezone. */
    public static ZoneId zoneOf(Patient patient) {
        try {
            return ZoneId.of(patient.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return FALLBACK_ZONE;
        }
    }

    /** The patient's calendar date at this instant. */
    public static LocalDate localDate(Patient patient, Instant at) {
        return LocalDate.ofInstant(at, zoneOf(patient));
    }

    /** The patient's wall-clock time at this instant. */
    public static LocalTime localTime(Patient patient, Instant at) {
        return LocalTime.ofInstant(at, zoneOf(patient));
    }

    /** Whether the reminder's repeat rule includes this date. Ignores active and effectiveFrom. */
    public static boolean fallsOn(Reminder reminder, LocalDate date) {
        return switch (reminder.getRepeatType()) {
            case DAILY -> true;
            case WEEKLY -> reminder.getRepeatDays().stream()
                    .map(DayOfWeekCode::toDayOfWeek)
                    .anyMatch(day -> day == date.getDayOfWeek());
            case ONCE -> date.equals(reminder.getOneOffDate());
        };
    }

    /**
     * The instant the reminder falls due on this date. A time skipped by a daylight-saving change
     * moves forward to the first valid instant, as {@link java.time.ZonedDateTime} resolves it.
     */
    public static Instant scheduledAt(Reminder reminder, LocalDate date) {
        return date.atTime(reminder.getScheduledTime()).atZone(zoneOf(reminder.getPatient())).toInstant();
    }

    /**
     * Whether an occurrence should exist for this reminder on this date: the reminder is live, its
     * repeat rule includes the date, and the slot is not earlier than the current schedule.
     */
    public static boolean isDueOn(Reminder reminder, LocalDate date) {
        return reminder.isLive()
                && fallsOn(reminder, date)
                && !scheduledAt(reminder, date).isBefore(reminder.getEffectiveFrom());
    }
}
