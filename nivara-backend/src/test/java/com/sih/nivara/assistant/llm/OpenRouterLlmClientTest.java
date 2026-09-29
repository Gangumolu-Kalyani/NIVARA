package com.sih.nivara.assistant.llm;

import com.sih.nivara.assistant.tool.JsonSchema;
import com.sih.nivara.assistant.tool.ToolDefinition;
import com.sih.nivara.support.StubOpenRouterServer;
import com.sih.nivara.support.StubOpenRouterServer.Scripted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.sih.nivara.support.StubOpenRouterServer.textResponse;
import static com.sih.nivara.support.StubOpenRouterServer.toolCallResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The OpenRouter client against a local stub: request format, parsing, retries and timeouts. */
class OpenRouterLlmClientTest {

    private static final String KEY = "sk-or-test-key-123";
    private static final String MODEL = "vendor/some-model:free";

    private final StubOpenRouterServer stub = new StubOpenRouterServer();
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void stop() {
        stub.close();
    }

    private OpenRouterLlmClient client(Duration requestTimeout) {
        return new OpenRouterLlmClient(new LlmProperties(LlmProperties.Provider.OPENROUTER, stub.baseUrl(), null, KEY,
                null, Duration.ofSeconds(2), requestTimeout, null, Duration.ofMillis(50), null, null, null, null), json);
    }

    private OpenRouterLlmClient client() {
        return client(Duration.ofSeconds(5));
    }

    private static LlmChatRequest request(LlmChatRequest.ToolChoice choice, Duration timeout) {
        ToolDefinition people = new ToolDefinition("get_people", "Lists people",
                Map.of("type", "object", "properties", Map.of("name", JsonSchema.string("Name", 120)),
                        "required", List.of(), "additionalProperties", false), false);
        return new LlmChatRequest(List.of(
                new LlmMessage.System("Be kind."),
                new LlmMessage.User("Who is Anil?"),
                new LlmMessage.Assistant(null, List.of(new ToolCall("call_1", "get_people", "{\"name\":\"Anil\"}"))),
                new LlmMessage.ToolResult("call_1", "get_people", "{\"status\":\"SUCCESS\"}")),
                List.of(people), choice, timeout);
    }

    private static LlmChatRequest request() {
        return request(LlmChatRequest.ToolChoice.AUTO, Duration.ofSeconds(10));
    }

    @Test
    void sendsAnOpenAiCompatibleRequestWithTheKeyOnlyInTheHeader() throws Exception {
        stub.enqueue(Scripted.ok(textResponse(MODEL, "Anil is your son.")));
        client().chat(request());

        StubOpenRouterServer.Received received = stub.received().get(0);
        assertEquals("POST", received.method());
        assertEquals("/api/v1/chat/completions", received.path());
        assertEquals("Bearer " + KEY, received.header("Authorization"));
        assertTrue(received.header("Content-Type").startsWith("application/json"));
        assertEquals("NIVARA", received.header("X-OpenRouter-Title"));
        assertFalse(received.body().contains(KEY), "the key is never in the body");

        JsonNode body = json.readTree(received.body());
        assertEquals("openrouter/free", body.get("model").asString(), "the free router by default");
        assertEquals(0.2, body.get("temperature").asDouble());
        assertEquals(800, body.get("max_tokens").asInt());
        assertEquals("auto", body.get("tool_choice").asString());

        JsonNode tool = body.get("tools").get(0);
        assertEquals("function", tool.get("type").asString());
        assertEquals("get_people", tool.get("function").get("name").asString());
        assertEquals("Lists people", tool.get("function").get("description").asString());
        assertEquals("object", tool.get("function").get("parameters").get("type").asString());

        JsonNode messages = body.get("messages");
        assertEquals(List.of("system", "user", "assistant", "tool"),
                List.of(messages.get(0).get("role").asString(), messages.get(1).get("role").asString(),
                        messages.get(2).get("role").asString(), messages.get(3).get("role").asString()));
        JsonNode call = messages.get(2).get("tool_calls").get(0);
        assertEquals("call_1", call.get("id").asString());
        assertEquals("function", call.get("type").asString());
        assertEquals("get_people", call.get("function").get("name").asString());
        assertEquals("{\"name\":\"Anil\"}", call.get("function").get("arguments").asString(), "arguments as a JSON string");
        assertEquals("call_1", messages.get(3).get("tool_call_id").asString());
        assertEquals("{\"status\":\"SUCCESS\"}", messages.get(3).get("content").asString());
    }

    @Test
    void sendsTheToolsEvenWhenToolsAreTurnedOff() throws Exception {
        stub.enqueue(Scripted.ok(textResponse(MODEL, "Done.")));
        client().chat(request(LlmChatRequest.ToolChoice.NONE, Duration.ofSeconds(10)));
        JsonNode body = json.readTree(stub.received().get(0).body());
        assertEquals("none", body.get("tool_choice").asString());
        assertEquals(1, body.get("tools").size());
    }

    @Test
    void readsAFinalAnswerAndWhichModelGaveIt() throws Exception {
        stub.enqueue(Scripted.ok(textResponse(MODEL, "Anil is your son.")));
        LlmResponse response = client().chat(request());
        LlmResponse.FinalText text = assertInstanceOf(LlmResponse.FinalText.class, response);
        assertEquals("Anil is your son.", text.content());
        assertEquals("openrouter:" + MODEL, text.generatedBy());
    }

    @Test
    void readsSeveralToolCalls() throws Exception {
        stub.enqueue(Scripted.ok(toolCallResponse(MODEL,
                "call_a", "get_people", "{\"name\":\"Anil\"}",
                "call_b", "search_memories", "{}")));
        LlmResponse.ToolCalls calls = assertInstanceOf(LlmResponse.ToolCalls.class, client().chat(request()));
        assertEquals(2, calls.calls().size());
        assertEquals(new ToolCall("call_a", "get_people", "{\"name\":\"Anil\"}"), calls.calls().get(0));
        assertEquals(new ToolCall("call_b", "search_memories", "{}"), calls.calls().get(1));
        assertEquals("openrouter:" + MODEL, calls.generatedBy());
    }

    @Test
    void retriesOnceOnRateLimitAndServerErrors() throws Exception {
        stub.enqueue(Scripted.status(429).withRetryAfter("0"), Scripted.ok(textResponse(MODEL, "Hello")));
        assertInstanceOf(LlmResponse.FinalText.class, client().chat(request()));
        assertEquals(2, stub.received().size());

        stub.reset();
        stub.enqueue(Scripted.status(502), Scripted.ok(textResponse(MODEL, "Hello")));
        assertInstanceOf(LlmResponse.FinalText.class, client().chat(request()));
        assertEquals(2, stub.received().size());

        // An error reported inside a 200 response counts as a provider failure
        stub.reset();
        stub.enqueue(Scripted.ok("{\"error\":{\"code\":502,\"message\":\"upstream\"}}"), Scripted.ok(textResponse(MODEL, "Hello")));
        assertInstanceOf(LlmResponse.FinalText.class, client().chat(request()));
        assertEquals(2, stub.received().size());
    }

    @Test
    void retriesAtMostOnce() {
        stub.enqueue(Scripted.status(500), Scripted.status(503), Scripted.ok(textResponse(MODEL, "too late")));
        LlmException e = assertThrows(LlmException.class, () -> client().chat(request()));
        assertEquals(LlmException.Reason.PROVIDER_ERROR, e.reason());
        assertEquals(2, stub.received().size());

        stub.reset();
        stub.enqueue(Scripted.status(429), Scripted.status(429));
        assertEquals(LlmException.Reason.RATE_LIMITED, assertThrows(LlmException.class, () -> client().chat(request())).reason());
        assertEquals(2, stub.received().size());
    }

    @Test
    void doesNotRetryARefusedRequestNorLeakTheKey() {
        for (int status : new int[] {400, 401, 402, 403}) {
            stub.reset();
            stub.enqueue(Scripted.status(status), Scripted.ok(textResponse(MODEL, "never")));
            LlmException e = assertThrows(LlmException.class, () -> client().chat(request()));
            assertEquals(LlmException.Reason.REJECTED, e.reason());
            assertEquals(1, stub.received().size(), "HTTP " + status + " is not retried");
            assertFalse(e.getMessage().contains(KEY));
        }
    }

    @Test
    void retriesATimeoutOnceWithinTheDeadline() throws Exception {
        // Each attempt may take 400 ms; the first answers after 1.5 s, the retry at once
        stub.enqueue(Scripted.ok(textResponse(MODEL, "slow")).delayed(1500), Scripted.ok(textResponse(MODEL, "Hello")));
        LlmResponse response = client(Duration.ofMillis(400)).chat(request(LlmChatRequest.ToolChoice.AUTO, Duration.ofSeconds(5)));
        assertEquals("Hello", assertInstanceOf(LlmResponse.FinalText.class, response).content());
        assertEquals(2, stub.received().size());

        stub.reset();
        stub.enqueue(Scripted.ok(textResponse(MODEL, "slow")).delayed(1500), Scripted.ok(textResponse(MODEL, "slow")).delayed(1500));
        LlmException e = assertThrows(LlmException.class,
                () -> client(Duration.ofMillis(400)).chat(request(LlmChatRequest.ToolChoice.AUTO, Duration.ofSeconds(5))));
        assertEquals(LlmException.Reason.TIMEOUT, e.reason());
        assertEquals(2, stub.received().size());
    }

    @Test
    void doesNotRetryWhenTheDeadlineLeavesNoRoom() {
        stub.enqueue(Scripted.ok(textResponse(MODEL, "slow")).delayed(1500), Scripted.ok(textResponse(MODEL, "Hello")));
        LlmException e = assertThrows(LlmException.class,
                () -> client(Duration.ofSeconds(5)).chat(request(LlmChatRequest.ToolChoice.AUTO, Duration.ofMillis(500))));
        assertEquals(LlmException.Reason.TIMEOUT, e.reason());
        assertEquals(1, stub.received().size());
    }

    @Test
    void rejectsResponsesItCannotRead() {
        for (String body : new String[] {"not json", "[]", "{\"choices\":[]}", "{\"choices\":[{\"index\":0}]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"x\",\"function\":{}}]}}]}"}) {
            stub.reset();
            stub.enqueue(Scripted.ok(body));
            LlmException e = assertThrows(LlmException.class, () -> client().chat(request()), body);
            assertEquals(LlmException.Reason.INVALID_RESPONSE, e.reason(), body);
            assertEquals(1, stub.received().size(), "not retried: " + body);
        }
    }

    @Test
    void anUnreachableProviderFailsWithoutRetrying() {
        String baseUrl = stub.baseUrl();
        stub.close();
        OpenRouterLlmClient unreachable = new OpenRouterLlmClient(new LlmProperties(LlmProperties.Provider.OPENROUTER,
                baseUrl, null, KEY, null, Duration.ofSeconds(1), Duration.ofSeconds(2), null, Duration.ofMillis(50),
                null, null, null, null), json);
        assertEquals(LlmException.Reason.UNAVAILABLE, assertThrows(LlmException.class, () -> unreachable.chat(request())).reason());
    }
}
