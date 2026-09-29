package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.PatientResponseType;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Body of "the patient answered a reminder". scheduledAt names the occurrence being answered;
 * when omitted, the reminder's most recent occurrence that has already fallen due is meant.
 */
public record ReminderResponseRequest(

        @NotNull
        PatientResponseType responseType,

        Instant scheduledAt) {
}
