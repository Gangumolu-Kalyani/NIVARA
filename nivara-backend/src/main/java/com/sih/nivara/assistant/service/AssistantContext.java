package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;

import java.time.ZoneId;

/**
 * Everything the assistant knows about who it is talking to, resolved by
 * {@link AssistantContextResolver} from the authenticated account on every request.
 *
 * <p>This is what later phases hand to the tool layer, so a tool never decides for itself whose
 * data it reads: in PATIENT mode patient is always the caller's own linked patient, and in
 * CAREGIVER mode it is a patient the caller was just verified to reach, or null.
 */
public record AssistantContext(
        AppUser caller,
        AssistantMode mode,
        Patient patient,
        String languageCode,
        ZoneId timezone) {

    public boolean hasPatient() {
        return patient != null;
    }
}
