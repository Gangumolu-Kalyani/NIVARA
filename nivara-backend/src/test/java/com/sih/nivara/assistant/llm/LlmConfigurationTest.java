package com.sih.nivara.assistant.llm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which model client runs, and the checks on its configuration. */
class LlmConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withUserConfiguration(LlmConfiguration.class);

    @Test
    void thePlaceholderIsTheDefault() {
        runner.run(context -> assertThat(context.getBean(LlmClient.class)).isInstanceOf(PlaceholderLlmClient.class));
        // LLM_PROVIDER= left empty in .env
        runner.withPropertyValues("nivara.assistant.llm.provider=")
                .run(context -> assertThat(context.getBean(LlmClient.class)).isInstanceOf(PlaceholderLlmClient.class));
        runner.withPropertyValues("nivara.assistant.llm.provider=placeholder")
                .run(context -> assertThat(context.getBean(LlmClient.class)).isInstanceOf(PlaceholderLlmClient.class));
    }

    @Test
    void openRouterIsUsedWhenConfiguredWithAKey() {
        runner.withPropertyValues("nivara.assistant.llm.provider=openrouter", "nivara.assistant.llm.api-key=sk-or-x")
                .run(context -> {
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(OpenRouterLlmClient.class);
                    LlmProperties properties = context.getBean(LlmProperties.class);
                    assertEquals("https://openrouter.ai/api/v1", properties.baseUrl());
                    assertEquals("openrouter/free", properties.model());
                    assertEquals(Duration.ofSeconds(30), properties.requestTimeout());
                    assertEquals(Duration.ofSeconds(60), properties.exchangeTimeout());
                    assertEquals(4, properties.maxRounds());
                    assertEquals(5, properties.maxToolCallsPerRound());
                    assertEquals(0.2, properties.temperature());
                    assertEquals(800, properties.maxTokens());
                });
    }

    @Test
    void openRouterWithoutAKeyOrWithAnUnknownProviderRefusesToStart() {
        runner.withPropertyValues("nivara.assistant.llm.provider=openrouter")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("nivara.assistant.llm.provider=openrouter", "nivara.assistant.llm.api-key=${OPENROUTER_API_KEY}")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("nivara.assistant.llm.provider=somebody-else")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void theKeyGoesOnlyToAnHttpsProviderAndIsNeverPrinted() {
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> properties("openrouter-url-unused", null));
        assertTrue(missing.getMessage().contains("OPENROUTER_API_KEY"));

        assertThrows(IllegalArgumentException.class, () -> properties("http://example.com/api/v1", "sk-or-secret"));
        properties("http://localhost:8089/api/v1", "sk-or-secret");
        LlmProperties ok = properties("https://openrouter.ai/api/v1", "sk-or-secret");
        assertFalse(ok.toString().contains("sk-or-secret"));
        assertTrue(ok.toString().contains("apiKey=****"));
    }

    private static LlmProperties properties(String baseUrl, String key) {
        return new LlmProperties(LlmProperties.Provider.OPENROUTER, baseUrl, null, key,
                null, null, null, null, null, null, null, null, null);
    }
}
