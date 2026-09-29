package com.sih.nivara.assistant.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Optional body of "start a conversation with the assistant".
 *
 * <p>patientUuid is for caregivers: the patient to talk about, which they must be able to reach.
 * A patient never needs it; if one is sent it must be the patient's own. languageCode overrides the
 * default language: the patient's own for a patient, the account's for a caregiver. Its format
 * mirrors ck_assistant_conversations_language_code (V7).
 */
public record ConversationStartRequest(

        UUID patientUuid,

        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String languageCode) {
}
