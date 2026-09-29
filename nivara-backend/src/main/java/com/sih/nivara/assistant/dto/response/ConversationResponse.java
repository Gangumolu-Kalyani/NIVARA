package com.sih.nivara.assistant.dto.response;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.entity.enums.ConversationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A conversation without its messages. patientUuid and patientName are null for a caregiver's
 * conversation about no particular patient. updatedAt is the time of the latest message.
 */
public record ConversationResponse(
        UUID uuid,
        AssistantMode mode,
        UUID patientUuid,
        String patientName,
        String languageCode,
        ConversationStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant closedAt) {
}
