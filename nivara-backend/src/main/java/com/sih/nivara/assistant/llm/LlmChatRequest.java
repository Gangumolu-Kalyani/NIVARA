package com.sih.nivara.assistant.llm;

import com.sih.nivara.assistant.tool.ToolDefinition;

import java.time.Duration;
import java.util.List;

/**
 * One call to a model: the conversation so far, the tools it may ask for, whether it may ask for
 * them this time, and how long the call may take at most.
 *
 * <p>Offering a tool is not permission to run it. Every tool call the model makes goes back
 * through ToolExecutor, which checks the caller and the patient itself. Tool results do carry
 * patient information to the model provider, which is why real patient data must only be used with
 * a provider whose data terms have been approved.
 *
 * @param tools   sent on every call, even when toolChoice is NONE, as OpenRouter requires
 * @param timeout the most this call may take, its one retry included: the rest of the exchange's
 *                time. Each single attempt is further capped by the client's own request timeout.
 */
public record LlmChatRequest(
        List<LlmMessage> messages,
        List<ToolDefinition> tools,
        ToolChoice toolChoice,
        Duration timeout) {

    public enum ToolChoice {
        /** The model decides whether to call tools. */
        AUTO,
        /** The model must answer in text: the last round, once the tool limit is reached. */
        NONE
    }

    public LlmChatRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
