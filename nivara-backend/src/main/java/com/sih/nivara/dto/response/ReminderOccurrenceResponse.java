package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.PatientResponseType;
import com.sih.nivara.entity.enums.ReminderCategory;
import com.sih.nivara.entity.enums.ReminderResponseStatus;

import java.time.Instant;
import java.util.UUID;

/** One scheduled instance of a reminder and what happened to it. */
public record ReminderOccurrenceResponse(
        UUID uuid,
        UUID reminderUuid,
        String reminderTitle,
        ReminderCategory category,
        UUID patientUuid,
        Instant scheduledAt,
        Instant notifiedAt,
        ReminderResponseStatus responseStatus,
        PatientResponseType responseType,
        Instant respondedAt,
        short nudgeCount,
        boolean escalated,
        Instant escalatedAt) {
}
