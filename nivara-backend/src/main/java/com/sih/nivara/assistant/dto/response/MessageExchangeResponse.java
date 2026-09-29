package com.sih.nivara.assistant.dto.response;

import java.util.UUID;

/** The answer to "say something": the stored message and the assistant's stored reply. */
public record MessageExchangeResponse(
        UUID conversationUuid,
        MessageResponse userMessage,
        MessageResponse reply) {
}
