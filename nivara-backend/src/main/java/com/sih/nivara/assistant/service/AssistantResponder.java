package com.sih.nivara.assistant.service;

import com.sih.nivara.assistant.entity.AssistantMessage;
import com.sih.nivara.assistant.entity.enums.MessageSender;
import com.sih.nivara.assistant.llm.LlmChatRequest;
import com.sih.nivara.assistant.llm.LlmClient;
import com.sih.nivara.assistant.llm.LlmException;
import com.sih.nivara.assistant.llm.LlmMessage;
import com.sih.nivara.assistant.llm.LlmProperties;
import com.sih.nivara.assistant.llm.LlmResponse;
import com.sih.nivara.assistant.llm.ToolCall;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.AssistantToolRegistry;
import com.sih.nivara.assistant.tool.ToolDefinition;
import com.sih.nivara.assistant.tool.ToolErrorCode;
import com.sih.nivara.assistant.tool.ToolExecutor;
import com.sih.nivara.assistant.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces the assistant's reply to a conversation: the loop between the language model and the
 * NIVARA tools.
 *
 * <ol>
 *   <li>The model gets the system prompt, the recent conversation and the tools of this
 *       conversation's mode.</li>
 *   <li>If it asks for tools, each call is run through {@link ToolExecutor} with this request's
 *       {@link AssistantContext}, so every authorization check applies; the model never runs a tool
 *       itself. The results go back to the model and it is asked again.</li>
 *   <li>After {@code maxRounds} rounds of tool calls the model gets one last call with tools turned
 *       off, and must answer.</li>
 * </ol>
 *
 * <p>Limits: at most {@code maxToolCallsPerRound} calls run per round (the rest are refused), and
 * the whole exchange must finish within {@code exchangeTimeout}. If the model fails, runs out of
 * time or answers with nothing, the reply is the fixed {@link #FALLBACK_REPLY}; the user is never
 * shown provider errors. Nothing here is logged except failure reasons: no prompts, tool results
 * or patient information.
 */
@Component
public class AssistantResponder {

    private static final Logger log = LoggerFactory.getLogger(AssistantResponder.class);

    public static final String FALLBACK_REPLY =
            "Sorry, I can't answer right now. Please try again in a moment.";
    public static final String FALLBACK_GENERATED_BY = "fallback";

    /** Replies longer than this are cut, to stay readable and within the column's purpose. */
    static final int MAX_REPLY_CHARS = 4000;

    /** A tool result longer than this is cut before it goes back to the model. */
    static final int MAX_TOOL_RESULT_CHARS = 12_000;

    /** The minimum time worth starting another model call with. */
    private static final Duration MINIMUM_CALL_TIME = Duration.ofSeconds(1);

    /** The assistant's reply and what produced it (a model id, or "fallback"). */
    public record Reply(String content, String generatedBy) {
    }

    private final LlmClient llmClient;
    private final ToolExecutor toolExecutor;
    private final AssistantToolRegistry toolRegistry;
    private final LlmProperties properties;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AssistantResponder(LlmClient llmClient,
                              ToolExecutor toolExecutor,
                              AssistantToolRegistry toolRegistry,
                              LlmProperties properties,
                              JsonMapper jsonMapper) {
        this.llmClient = llmClient;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.clock = Clock.systemUTC();
    }

    /**
     * The reply to the conversation, whose history ends with the message to answer. Never throws
     * for a model failure: that becomes the fallback reply.
     */
    public Reply respond(AssistantContext context, List<AssistantMessage> history) {
        Instant start = clock.instant();
        Instant deadline = start.plus(properties.exchangeTimeout());
        List<ToolDefinition> tools = toolRegistry.toolsFor(context.mode());

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(new LlmMessage.System(SystemPrompts.forContext(context, start)));
        for (AssistantMessage message : history) {
            messages.add(message.getSender() == MessageSender.USER
                    ? new LlmMessage.User(message.getContent())
                    : LlmMessage.Assistant.text(message.getContent()));
        }

        try {
            for (int round = 1; round <= properties.maxRounds() + 1; round++) {
                boolean lastRound = round > properties.maxRounds();
                Duration remaining = Duration.between(clock.instant(), deadline);
                if (remaining.compareTo(MINIMUM_CALL_TIME) < 0) {
                    log.warn("Assistant exchange ran out of time after {} round(s)", round - 1);
                    return fallback();
                }
                LlmResponse response;
                try {
                    response = llmClient.chat(new LlmChatRequest(messages, tools,
                            lastRound ? LlmChatRequest.ToolChoice.NONE : LlmChatRequest.ToolChoice.AUTO, remaining));
                } catch (RuntimeException e) {
                    // A bug or misconfiguration in the client, not a provider answer: the user still gets
                    // the fallback rather than an HTTP 500. Only the type is logged; its message could
                    // carry request details.
                    log.error("Assistant model client failed unexpectedly ({})", e.getClass().getName());
                    return fallback();
                }
                if (response == null) {
                    log.error("Assistant model client returned no response");
                    return fallback();
                }

                if (response instanceof LlmResponse.FinalText text) {
                    return finalReply(text);
                }
                LlmResponse.ToolCalls calls = (LlmResponse.ToolCalls) response;
                if (lastRound) {
                    log.warn("Model still asked for tools after {} rounds", properties.maxRounds());
                    return fallback();
                }
                messages.add(new LlmMessage.Assistant(calls.content(), calls.calls()));
                runTools(context, calls.calls(), messages);
            }
            return fallback();
        } catch (LlmException e) {
            log.warn("Assistant model call failed: {} ({})", e.reason(), e.getMessage());
            return fallback();
        }
    }

    /** Runs each call in order, adding one result message per call, matched by its id. */
    private void runTools(AssistantContext context, List<ToolCall> calls, List<LlmMessage> messages) {
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            ToolResult result = i < properties.maxToolCallsPerRound()
                    ? runTool(context, call)
                    : ToolResult.error(call.name(), ToolErrorCode.CALL_LIMIT_EXCEEDED,
                            "Too many tool calls at once; at most " + properties.maxToolCallsPerRound() + " run per turn");
            messages.add(new LlmMessage.ToolResult(call.id(), call.name(), serialize(result)));
        }
    }

    private ToolResult runTool(AssistantContext context, ToolCall call) {
        AssistantTool<?> tool = toolRegistry.find(call.name()).orElse(null);
        if (tool != null && tool.requiresConfirmation()) {
            // No confirmation flow exists yet, so a tool that changes data never runs from a model's request.
            return ToolResult.error(call.name(), ToolErrorCode.CONFIRMATION_REQUIRED,
                    "This action needs the user's confirmation, which is not available yet");
        }
        JsonNode arguments;
        try {
            String json = call.argumentsJson();
            arguments = json == null || json.isBlank() ? null : jsonMapper.readTree(json);
        } catch (JacksonException e) {
            return ToolResult.error(call.name(), ToolErrorCode.INVALID_ARGUMENTS, "Arguments are not valid JSON");
        }
        return toolExecutor.execute(context, call.name(), arguments);
    }

    /** The result as JSON for the model; an oversized one is cut and marked truncated. */
    private String serialize(ToolResult result) {
        String json = jsonMapper.writeValueAsString(result);
        if (json.length() <= MAX_TOOL_RESULT_CHARS) {
            return json;
        }
        ObjectNode cut = jsonMapper.createObjectNode();
        cut.put("tool", result.tool());
        cut.put("status", result.status().name());
        cut.put("truncated", true);
        cut.put("partialResult", json.substring(0, MAX_TOOL_RESULT_CHARS));
        return jsonMapper.writeValueAsString(cut);
    }

    private Reply finalReply(LlmResponse.FinalText text) {
        String content = text.content() == null ? "" : text.content().strip();
        if (content.isEmpty()) {
            log.warn("Model answered with an empty reply");
            return fallback();
        }
        if (content.length() > MAX_REPLY_CHARS) {
            content = content.substring(0, MAX_REPLY_CHARS - 1).stripTrailing() + "…";
        }
        String generatedBy = text.generatedBy() == null || text.generatedBy().isBlank()
                ? "unknown" : text.generatedBy();
        return new Reply(content, generatedBy.length() <= 100 ? generatedBy : generatedBy.substring(0, 100));
    }

    private static Reply fallback() {
        return new Reply(FALLBACK_REPLY, FALLBACK_GENERATED_BY);
    }
}
