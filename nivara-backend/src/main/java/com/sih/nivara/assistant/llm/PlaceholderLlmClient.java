package com.sih.nivara.assistant.llm;

import org.springframework.stereotype.Component;

/**
 * Stands in for a language model until one is connected (Phase 4): always gives the same answer,
 * makes no network call, and needs no configuration or credentials.
 */
@Component
public class PlaceholderLlmClient implements LlmClient {

    public static final String REPLY = "Assistant is not connected to the language model yet.";
    public static final String GENERATED_BY = "placeholder";

    @Override
    public LlmReply reply(LlmRequest request) {
        return new LlmReply(REPLY, GENERATED_BY);
    }
}
