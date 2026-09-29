package com.sih.nivara.assistant.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration of the assistant's language model, under {@code nivara.assistant.llm}.
 *
 * <p>The default provider is PLACEHOLDER, which needs nothing else, so the application and its
 * tests start without a key. With OPENROUTER, the API key must be set (OPENROUTER_API_KEY, from
 * the environment or the git-ignored .env file) or the application refuses to start. The key is
 * never logged: {@link #toString} masks it.
 *
 * @param requestTimeout  the most one model call may take, a retry included
 * @param exchangeTimeout the most answering one user message may take, all rounds included
 * @param maxRounds       how many times the model may ask for tools before it must answer
 * @param maxToolCallsPerRound tool calls beyond this in one round are refused, not run
 * @param retryBackoff    the wait before the single retry, unless the provider asks for less
 */
@ConfigurationProperties(prefix = "nivara.assistant.llm")
public record LlmProperties(
        Provider provider,
        String baseUrl,
        String model,
        String apiKey,
        String appName,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration exchangeTimeout,
        Duration retryBackoff,
        Integer maxRounds,
        Integer maxToolCallsPerRound,
        Double temperature,
        Integer maxTokens) {

    public enum Provider {
        PLACEHOLDER,
        OPENROUTER
    }

    public LlmProperties {
        provider = provider != null ? provider : Provider.PLACEHOLDER;
        baseUrl = blankToNull(baseUrl) != null ? baseUrl.strip() : "https://openrouter.ai/api/v1";
        model = blankToNull(model) != null ? model.strip() : "openrouter/free";
        apiKey = blankToNull(apiKey);
        appName = blankToNull(appName) != null ? appName.strip() : "NIVARA";
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(30);
        exchangeTimeout = exchangeTimeout != null ? exchangeTimeout : Duration.ofSeconds(60);
        retryBackoff = retryBackoff != null ? retryBackoff : Duration.ofSeconds(1);
        maxRounds = maxRounds != null ? maxRounds : 4;
        maxToolCallsPerRound = maxToolCallsPerRound != null ? maxToolCallsPerRound : 5;
        temperature = temperature != null ? temperature : 0.2;
        maxTokens = maxTokens != null ? maxTokens : 800;

        if (maxRounds < 1 || maxToolCallsPerRound < 1 || maxTokens < 1) {
            throw new IllegalArgumentException("nivara.assistant.llm: max-rounds, max-tool-calls-per-round "
                    + "and max-tokens must be at least 1");
        }
        for (Duration duration : new Duration[] {connectTimeout, requestTimeout, exchangeTimeout}) {
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("nivara.assistant.llm: timeouts must be positive");
            }
        }
        if (provider == Provider.OPENROUTER) {
            if (apiKey == null) {
                throw new IllegalArgumentException("OPENROUTER_API_KEY is not set. The assistant's provider is "
                        + "openrouter, which needs an API key; set it, or set LLM_PROVIDER=placeholder");
            }
            requireSafeBaseUrl(baseUrl);
        }
    }

    /**
     * The key travels only to its provider, so the base URL must be HTTPS. Plain HTTP is allowed for
     * localhost only, which is what tests use.
     */
    private static void requireSafeBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nivara.assistant.llm.base-url is not a valid URL");
        }
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && local)) {
            throw new IllegalArgumentException("nivara.assistant.llm.base-url must use https");
        }
    }

    /** Treats blank values, and unresolved ${...} placeholders, as unset. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() || value.strip().startsWith("${") ? null : value;
    }

    @Override
    public String toString() {
        return "LlmProperties[provider=" + provider + ", baseUrl=" + baseUrl + ", model=" + model
                + ", apiKey=" + (apiKey == null ? "unset" : "****") + ", appName=" + appName
                + ", connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout
                + ", exchangeTimeout=" + exchangeTimeout + ", retryBackoff=" + retryBackoff
                + ", maxRounds=" + maxRounds + ", maxToolCallsPerRound=" + maxToolCallsPerRound
                + ", temperature=" + temperature + ", maxTokens=" + maxTokens + "]";
    }
}
