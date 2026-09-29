package com.sih.nivara.assistant.dto.mapper;

import com.sih.nivara.assistant.dto.response.ConversationDetailResponse;
import com.sih.nivara.assistant.dto.response.ConversationResponse;
import com.sih.nivara.assistant.dto.response.MessageExchangeResponse;
import com.sih.nivara.assistant.dto.response.MessageResponse;
import com.sih.nivara.assistant.entity.AssistantConversation;
import com.sih.nivara.assistant.entity.AssistantMessage;
import com.sih.nivara.assistant.service.AssistantService;

/**
 * Converts assistant conversations and messages to responses. Reads the conversation's patient,
 * which is lazy, so the conversation must have been loaded with it fetched. The account that sent
 * a message is kept for auditing but not returned: a conversation has only one owner.
 */
public final class AssistantMapper {

    private AssistantMapper() {
        // utility class
    }

    public static ConversationResponse toResponse(AssistantConversation conversation) {
        return new ConversationResponse(
                conversation.getUuid(),
                conversation.getMode(),
                conversation.getPatient() == null ? null : conversation.getPatient().getUuid(),
                conversation.getPatient() == null ? null : conversation.getPatient().getFullName(),
                conversation.getLanguageCode(),
                conversation.getStatus(),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                conversation.getClosedAt());
    }

    public static MessageResponse toResponse(AssistantMessage message) {
        return new MessageResponse(
                message.getUuid(),
                message.getSequenceNumber(),
                message.getSender(),
                message.getContent(),
                message.getCreatedAt());
    }

    public static ConversationDetailResponse toDetailResponse(AssistantService.ConversationView view) {
        return new ConversationDetailResponse(
                toResponse(view.conversation()),
                view.messages().stream().map(AssistantMapper::toResponse).toList());
    }

    public static MessageExchangeResponse toExchangeResponse(AssistantService.Exchange exchange) {
        return new MessageExchangeResponse(
                exchange.conversation().getUuid(),
                toResponse(exchange.userMessage()),
                toResponse(exchange.reply()));
    }
}
