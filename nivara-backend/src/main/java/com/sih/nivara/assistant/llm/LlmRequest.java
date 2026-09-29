package com.sih.nivara.assistant.llm;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.entity.enums.MessageSender;
import com.sih.nivara.assistant.tool.ToolDefinition;

import java.util.List;

/**
 * What a model needs to produce the next reply: who it is talking to, in which language, the
 * recent conversation, oldest first, ending with the message to answer, and the tools this kind of
 * conversation may use. Offering a tool is not permission to run it: every call still goes through
 * ToolExecutor, which checks the caller and the patient itself.
 *
 * <p>Deliberately provider-neutral and free of entities: no account, patient record or identifier
 * reaches a model through this type.
 */
public record LlmRequest(
        AssistantMode mode,
        String languageCode,
        List<Message> messages,
        List<ToolDefinition> tools) {

    public LlmRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }

    /** One turn of the conversation as the model sees it. */
    public record Message(MessageSender sender, String content) {
    }
}
