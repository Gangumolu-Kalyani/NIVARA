package com.sih.nivara.assistant.llm;

import com.sih.nivara.assistant.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@link LlmClient} for OpenRouter's OpenAI-compatible Chat Completions API
 * ({@code POST {baseUrl}/chat/completions}), with tool calling.
 *
 * <p>Only translates and transports: {@link LlmChatRequest} to OpenRouter's JSON and its answer to
 * {@link LlmResponse}. The API key is sent in the Authorization header only. Nothing sent or
 * received is logged: failures log their reason and HTTP status, never a prompt, a tool result,
 * patient information or the key.
 *
 * <p>Each HTTP attempt may take request-timeout (30 seconds by default) at most. It is retried once,
 * if the request's remaining time allows, on a timeout, HTTP 429 or 408, HTTP 5xx, or an error
 * reported inside a 200 response. Everything else fails at once.
 */
public class OpenRouterLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterLlmClient.class);

    /** A retry is only worth making with at least this much time left. */
    private static final Duration MINIMUM_ATTEMPT = Duration.ofSeconds(1);

    /** The longest a Retry-After from the provider is honoured; longer ones exceed our budget anyway. */
    private static final Duration MAXIMUM_RETRY_AFTER = Duration.ofSeconds(5);

    private final LlmProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final URI endpoint;

    public OpenRouterLlmClient(LlmProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.endpoint = URI.create(properties.baseUrl().replaceAll("/+$", "") + "/chat/completions");
    }

    @Override
    public LlmResponse chat(LlmChatRequest request) throws LlmException {
        Instant deadline = Instant.now().plus(request.timeout());
        String body = jsonMapper.writeValueAsString(toRequestBody(request));
        try {
            return attempt(body, deadline);
        } catch (RetryableAttemptException first) {
            Duration wait = first.retryAfter() != null ? first.retryAfter() : properties.retryBackoff();
            if (Duration.between(Instant.now(), deadline).compareTo(wait.plus(MINIMUM_ATTEMPT)) < 0) {
                throw first.cause();
            }
            log.info("Model call failed ({}); retrying once", first.cause().reason());
            sleep(wait);
            try {
                return attempt(body, deadline);
            } catch (RetryableAttemptException second) {
                throw second.cause();
            }
        }
    }

    // ---- request -----------------------------------------------------------------------------

    ObjectNode toRequestBody(LlmChatRequest request) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("model", properties.model());
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            messages.add(toJson(message));
        }
        if (!request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolDefinition definition : request.tools()) {
                ObjectNode function = jsonMapper.createObjectNode();
                function.put("name", definition.name());
                function.put("description", definition.description());
                function.set("parameters", jsonMapper.valueToTree(definition.parametersSchema()));
                tools.addObject().put("type", "function").set("function", function);
            }
            body.put("tool_choice", request.toolChoice().name().toLowerCase(Locale.ROOT));
        }
        body.put("temperature", properties.temperature());
        body.put("max_tokens", properties.maxTokens());
        return body;
    }

    private ObjectNode toJson(LlmMessage message) {
        ObjectNode json = jsonMapper.createObjectNode();
        switch (message) {
            case LlmMessage.System system -> json.put("role", "system").put("content", system.content());
            case LlmMessage.User user -> json.put("role", "user").put("content", user.content());
            case LlmMessage.Assistant assistant -> {
                json.put("role", "assistant").put("content", assistant.content());
                if (!assistant.toolCalls().isEmpty()) {
                    ArrayNode calls = json.putArray("tool_calls");
                    for (ToolCall call : assistant.toolCalls()) {
                        ObjectNode function = jsonMapper.createObjectNode()
                                .put("name", call.name())
                                .put("arguments", call.argumentsJson());
                        calls.addObject().put("id", call.id()).put("type", "function").set("function", function);
                    }
                }
            }
            case LlmMessage.ToolResult result -> json.put("role", "tool")
                    .put("tool_call_id", result.toolCallId())
                    .put("name", result.toolName())
                    .put("content", result.content());
        }
        return json;
    }

    // ---- one attempt -------------------------------------------------------------------------

    /** One HTTP call, allowed request-timeout at most, and never past the request's deadline. */
    private LlmResponse attempt(String body, Instant deadline) throws LlmException, RetryableAttemptException {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.compareTo(Duration.ZERO) <= 0) {
            throw new LlmException(LlmException.Reason.TIMEOUT, "No time left for the model call");
        }
        Duration timeout = remaining.compareTo(properties.requestTimeout()) < 0 ? remaining : properties.requestTimeout();
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + properties.apiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-OpenRouter-Title", properties.appName())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw retryable(new LlmException(LlmException.Reason.TIMEOUT, "The model call timed out"), null);
        } catch (IOException e) {
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "Could not reach the model provider");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "The model call was interrupted");
        }

        int status = response.statusCode();
        if (status == 429) {
            throw retryable(new LlmException(LlmException.Reason.RATE_LIMITED, "Model provider rate limit (HTTP 429)"),
                    retryAfter(response));
        }
        if (status == 408 || status >= 500) {
            throw retryable(new LlmException(LlmException.Reason.PROVIDER_ERROR, "Model provider failed (HTTP " + status + ")"),
                    retryAfter(response));
        }
        if (status != 200) {
            throw new LlmException(LlmException.Reason.REJECTED, "Model provider refused the request (HTTP " + status + ")");
        }
        return parse(response.body());
    }

    LlmResponse parse(String responseBody) throws LlmException, RetryableAttemptException {
        JsonNode root;
        try {
            root = jsonMapper.readTree(responseBody);
        } catch (JacksonException e) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "The model response is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "The model response is not a JSON object");
        }
        // OpenRouter can report a failure inside a 200 response
        if (root.hasNonNull("error")) {
            throw retryable(new LlmException(LlmException.Reason.PROVIDER_ERROR,
                    "Model provider reported an error (code " + text(root.get("error"), "code") + ")"), null);
        }
        JsonNode choices = root.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "The model response has no choices");
        }
        JsonNode choice = choices.get(0);
        if (choice.hasNonNull("error") || "error".equals(text(choice, "finish_reason"))) {
            throw retryable(new LlmException(LlmException.Reason.PROVIDER_ERROR,
                    "The model stopped with an error"), null);
        }
        JsonNode message = choice.get("message");
        if (message == null || !message.isObject()) {
            throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "The model response has no message");
        }

        String generatedBy = generatedBy(text(root, "model"));
        String content = text(message, "content");
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls != null && toolCalls.isArray() && !toolCalls.isEmpty()) {
            List<ToolCall> calls = new ArrayList<>();
            int index = 0;
            for (JsonNode call : toolCalls) {
                index++;
                JsonNode function = call.get("function");
                String name = function == null ? null : text(function, "name");
                if (name == null) {
                    throw new LlmException(LlmException.Reason.INVALID_RESPONSE, "A tool call has no function name");
                }
                JsonNode arguments = function.get("arguments");
                String argumentsJson = arguments == null || arguments.isNull() ? "{}"
                        : arguments.isString() ? arguments.asString() : arguments.toString();
                String id = text(call, "id");
                calls.add(new ToolCall(id != null ? id : "call_" + index, name, argumentsJson));
            }
            return new LlmResponse.ToolCalls(calls, content, generatedBy);
        }
        return new LlmResponse.FinalText(content == null ? "" : content, generatedBy);
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** openrouter:{the model that actually answered}, cut to fit assistant_messages.generated_by. */
    private String generatedBy(String servedModel) {
        String value = "openrouter:" + (servedModel != null ? servedModel : properties.model());
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private static Duration retryAfter(HttpResponse<String> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                long seconds = Long.parseLong(value.strip());
                return seconds >= 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }).filter(wait -> wait.compareTo(MAXIMUM_RETRY_AFTER) <= 0).orElse(null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static void sleep(Duration wait) throws LlmException {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Reason.UNAVAILABLE, "The model call was interrupted");
        }
    }

    private static RetryableAttemptException retryable(LlmException cause, Duration retryAfter) {
        return new RetryableAttemptException(cause, retryAfter);
    }

    /** An attempt that failed in a way one more attempt might fix. */
    static final class RetryableAttemptException extends Exception {
        private final LlmException cause;
        private final Duration retryAfter;

        RetryableAttemptException(LlmException cause, Duration retryAfter) {
            super(cause.getMessage(), null, false, false);
            this.cause = cause;
            this.retryAfter = retryAfter;
        }

        LlmException cause() {
            return cause;
        }

        Duration retryAfter() {
            return retryAfter;
        }
    }
}
