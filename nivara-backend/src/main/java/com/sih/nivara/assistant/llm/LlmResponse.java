package com.sih.nivara.assistant.llm;

import java.util.List;

/**
 * What a model answered: either a final reply for the user, or a request to run tools first.
 * generatedBy names the model that produced it, such as {@code openrouter:vendor/model:free}; it
 * is stored with the reply (assistant_messages.generated_by).
 */
public sealed interface LlmResponse {

    String generatedBy();

    /** The reply to show the user. */
    record FinalText(String content, String generatedBy) implements LlmResponse {
    }

    /**
     * The model wants these tools run before it answers. content is any text the model sent
     * alongside the calls, often null; it is replayed to the model with the calls.
     */
    record ToolCalls(List<ToolCall> calls, String content, String generatedBy) implements LlmResponse {
        public ToolCalls {
            calls = List.copyOf(calls);
        }
    }
}
