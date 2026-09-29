package com.sih.nivara.assistant.llm;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.entity.enums.MessageSender;

import java.util.List;

/**
 * What a model needs to produce the next reply: who it is talking to, in which language, and the
 * recent conversation, oldest first, ending with the message to answer.
 *
 * <p>Deliberately provider-neutral and free of entities: no account, patient record or identifier
 * reaches a model through this type.
 */
public record LlmRequest(
        AssistantMode mode,
        String languageCode,
        List<Message> messages) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }

    /** One turn of the conversation as the model sees it. */
    public record Message(MessageSender sender, String content) {
    }
}
