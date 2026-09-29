package com.sih.nivara.assistant.llm;

/**
 * The one seam between the assistant and a language model.
 *
 * <p>{@code AssistantService} decides who is asking, about which patient, and what may be seen;
 * an implementation of this interface only turns a conversation into the assistant's next reply.
 * It never receives an account, a token or a database handle, so a model can never make an
 * authorization decision.
 *
 * <p>Phase 2 ships {@link PlaceholderLlmClient}. A real model (Phase 4) is another implementation
 * of this interface; AssistantService does not change.
 */
public interface LlmClient {

    /** The assistant's next reply to this conversation. */
    LlmReply reply(LlmRequest request);
}
