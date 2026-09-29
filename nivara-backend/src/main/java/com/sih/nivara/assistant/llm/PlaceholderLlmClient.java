package com.sih.nivara.assistant.llm;

/**
 * Stands in for a language model when none is configured, which is the default: always gives the
 * same answer, makes no network call, and needs no configuration or credentials. It ignores the
 * tools it is offered and never asks for one to run. Created by {@link LlmConfiguration}.
 */
public class PlaceholderLlmClient implements LlmClient {

    public static final String REPLY = "Assistant is not connected to the language model yet.";
    public static final String GENERATED_BY = "placeholder";

    @Override
    public LlmResponse chat(LlmChatRequest request) {
        return new LlmResponse.FinalText(REPLY, GENERATED_BY);
    }
}
