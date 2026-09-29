package com.sih.nivara.assistant.llm;

/**
 * The one seam between the assistant and a language model provider.
 *
 * <p>An implementation only talks to its provider: it turns an {@link LlmChatRequest} into the
 * provider's format, sends it, and turns the answer into an {@link LlmResponse}. It never runs a
 * tool, never sees an account or token, and makes no authorization decision. The tool-calling loop
 * lives in AssistantResponder, and tools run only through ToolExecutor.
 *
 * <p>Which implementation is used is decided by {@code nivara.assistant.llm.provider}:
 * {@link PlaceholderLlmClient} by default, or {@link OpenRouterLlmClient}.
 */
public interface LlmClient {

    /**
     * Asks the model for its next turn, within request.timeout(), retrying at most once when a
     * retry may help. Throws when the model could not answer.
     */
    LlmResponse chat(LlmChatRequest request) throws LlmException;
}
