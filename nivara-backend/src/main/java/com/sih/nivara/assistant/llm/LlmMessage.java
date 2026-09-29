package com.sih.nivara.assistant.llm;

import java.util.List;

/**
 * One turn of the conversation a model is given. Provider-neutral: a client translates these into
 * its provider's wire format.
 */
public sealed interface LlmMessage {

    /** How the assistant must behave in this conversation. Always the first message. */
    record System(String content) implements LlmMessage {
    }

    /** Something the user said. */
    record User(String content) implements LlmMessage {
    }

    /**
     * Something the assistant said earlier. toolCalls is empty for an ordinary reply, and holds the
     * calls the model asked for when it asked for tools; content may then be null.
     */
    record Assistant(String content, List<ToolCall> toolCalls) implements LlmMessage {
        public Assistant {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }

        public static Assistant text(String content) {
            return new Assistant(content, List.of());
        }
    }

    /** The backend's answer to one tool call, as JSON, matched to the call by its id. */
    record ToolResult(String toolCallId, String toolName, String content) implements LlmMessage {
    }
}
