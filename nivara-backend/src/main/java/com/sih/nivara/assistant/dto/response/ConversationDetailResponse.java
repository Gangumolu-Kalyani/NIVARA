package com.sih.nivara.assistant.dto.response;

import java.util.List;

/** A conversation with all its messages, oldest first. */
public record ConversationDetailResponse(
        ConversationResponse conversation,
        List<MessageResponse> messages) {
}
