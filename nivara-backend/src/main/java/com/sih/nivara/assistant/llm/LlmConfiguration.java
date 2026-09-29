package com.sih.nivara.assistant.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chooses the assistant's language model from {@code nivara.assistant.llm.provider}, as validated
 * by {@link LlmProperties}: {@link PlaceholderLlmClient} when unset or blank, which is the default,
 * or {@link OpenRouterLlmClient} for {@code openrouter}. An unknown provider fails startup.
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfiguration {

    @Bean
    LlmClient llmClient(LlmProperties properties, JsonMapper jsonMapper) {
        return switch (properties.provider()) {
            case PLACEHOLDER -> new PlaceholderLlmClient();
            case OPENROUTER -> new OpenRouterLlmClient(properties, jsonMapper);
        };
    }
}
