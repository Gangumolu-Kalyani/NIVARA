package com.sih.nivara.assistant.dto.response;

import com.sih.nivara.assistant.entity.enums.MessageSender;

import java.time.Instant;
import java.util.UUID;

/** One message. sequenceNumber orders a conversation: 1, 2, 3 ... */
public record MessageResponse(
        UUID uuid,
        int sequenceNumber,
        MessageSender sender,
        String content,
        Instant createdAt) {
}
