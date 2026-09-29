package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.ReminderCreateRequest;
import com.sih.nivara.dto.request.ReminderUpdateRequest;
import com.sih.nivara.dto.response.ReminderOccurrenceResponse;
import com.sih.nivara.dto.response.ReminderResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.ReminderOccurrence;
import com.sih.nivara.entity.enums.ReminderRepeatType;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Explicit conversion between the reminder DTOs and {@link Reminder} and
 * {@link ReminderOccurrence}.
 *
 * <p>Pure field copying: the repeat rules are checked by ReminderService before these run. The
 * response methods read the patient and reminder associations, which are lazy, so the entities
 * must have been loaded with them fetched.
 */
public final class ReminderMapper {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private ReminderMapper() {
        // utility class
    }

    /** Builds a new reminder. Omitted optional fields keep the entity and column defaults. */
    public static Reminder toEntity(ReminderCreateRequest request, Patient patient, AppUser createdByUser) {
        Reminder reminder = new Reminder(patient, request.category(), request.title().trim(),
                request.scheduledTime().withNano(0), createdByUser);
        reminder.setInstructions(request.instructions());
        if (request.repeatType() != null) {
            reminder.setRepeatType(request.repeatType());
        }
        reminder.setRepeatDays(reminder.getRepeatType() == ReminderRepeatType.WEEKLY ? request.repeatDays() : null);
        reminder.setOneOffDate(reminder.getRepeatType() == ReminderRepeatType.ONCE ? request.oneOffDate() : null);
        if (request.escalateAfterMissed() != null) {
            reminder.setEscalateAfterMissed(request.escalateAfterMissed());
        }
        if (request.active() != null) {
            reminder.setActive(request.active());
        }
        return reminder;
    }

    /** Copies a full-replacement update onto an existing reminder. */
    public static void applyUpdate(ReminderUpdateRequest request, Reminder reminder) {
        reminder.setCategory(request.category());
        reminder.setTitle(request.title().trim());
        reminder.setInstructions(request.instructions());
        reminder.setScheduledTime(request.scheduledTime().withNano(0));
        reminder.setRepeatType(request.repeatType());
        reminder.setRepeatDays(request.repeatType() == ReminderRepeatType.WEEKLY ? request.repeatDays() : null);
        reminder.setOneOffDate(request.repeatType() == ReminderRepeatType.ONCE ? request.oneOffDate() : null);
        reminder.setEscalateAfterMissed(request.escalateAfterMissed());
        reminder.setActive(request.active());
    }

    public static ReminderResponse toResponse(Reminder reminder) {
        return new ReminderResponse(
                reminder.getUuid(),
                reminder.getPatient().getUuid(),
                reminder.getCategory(),
                reminder.getTitle(),
                reminder.getInstructions(),
                reminder.getScheduledTime().format(TIME),
                reminder.getRepeatType(),
                List.copyOf(reminder.getRepeatDays()),
                reminder.getOneOffDate(),
                reminder.getEscalateAfterMissed(),
                reminder.isActive(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt());
    }

    public static ReminderOccurrenceResponse toOccurrenceResponse(ReminderOccurrence occurrence) {
        Reminder reminder = occurrence.getReminder();
        return new ReminderOccurrenceResponse(
                occurrence.getUuid(),
                reminder.getUuid(),
                reminder.getTitle(),
                reminder.getCategory(),
                occurrence.getPatient().getUuid(),
                occurrence.getScheduledAt(),
                occurrence.getNotifiedAt(),
                occurrence.getResponseStatus(),
                occurrence.getResponseType(),
                occurrence.getRespondedAt(),
                occurrence.getNudgeCount(),
                occurrence.isEscalated(),
                occurrence.getEscalatedAt());
    }
}
