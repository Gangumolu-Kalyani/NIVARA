package com.sih.nivara.assistant.llm;

/**
 * A model's reply. generatedBy names what produced it, such as a model id, and is stored with
 * the message (assistant_messages.generated_by) so every reply can be traced to its source.
 */
public record LlmReply(String content, String generatedBy) {
}
