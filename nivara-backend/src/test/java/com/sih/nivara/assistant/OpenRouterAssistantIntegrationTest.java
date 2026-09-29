package com.sih.nivara.assistant;

import com.sih.nivara.assistant.service.AssistantResponder;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import com.sih.nivara.support.StubOpenRouterServer;
import com.sih.nivara.support.StubOpenRouterServer.Scripted;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static com.sih.nivara.support.StubOpenRouterServer.textResponse;
import static com.sih.nivara.support.StubOpenRouterServer.toolCallResponse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole application with provider=openrouter, talking to a local stub instead of OpenRouter:
 * proves the real client is wired in and the loop works over HTTP. Never contacts OpenRouter.
 */
class OpenRouterAssistantIntegrationTest extends EmbeddedPostgresIntegrationTest {

    private static final StubOpenRouterServer STUB = new StubOpenRouterServer();
    private static final String KEY = "sk-or-integration-test";
    private static final String SERVED = "vendor/free-model:free";

    @DynamicPropertySource
    static void openRouter(DynamicPropertyRegistry registry) {
        registry.add("nivara.assistant.llm.provider", () -> "openrouter");
        registry.add("nivara.assistant.llm.base-url", STUB::baseUrl);
        registry.add("nivara.assistant.llm.api-key", () -> KEY);
        registry.add("nivara.assistant.llm.retry-backoff", () -> "PT0.05S");
    }

    @AfterAll
    static void stopStub() {
        STUB.close();
    }

    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetStub() {
        STUB.reset();
    }

    @Test
    void aPatientsQuestionIsAnsweredWithAToolCallThroughOpenRouter() {
        String caregiver = caregiverToken("Wired Carer");
        String patient = createPatient(caregiver, "Wired Patient");
        expect(201, "POST", "/api/patients/" + patient + "/people", caregiver,
                "{\"fullName\":\"Anil Rao\",\"relationship\":\"SON\"}");
        String patientToken = patientToken(caregiver, patient);
        String conversation = text(expect(201, "POST", "/api/assistant/conversations", patientToken, null).body(), "uuid");

        STUB.enqueue(Scripted.ok(toolCallResponse(SERVED, "call_9", "get_people", "{\"name\":\"Anil\"}")),
                Scripted.ok(textResponse(SERVED, "Anil is your son.")));

        JsonNode exchange = expect(200, "POST", "/api/assistant/conversations/" + conversation + "/messages",
                patientToken, "{\"content\":\"Who is Anil?\"}").body();
        assertEquals("Anil is your son.", text(exchange.get("reply"), "content"));
        assertEquals(2, STUB.received().size());

        StubOpenRouterServer.Received first = STUB.received().get(0);
        assertEquals("Bearer " + KEY, first.header("Authorization"));
        JsonNode firstBody = jsonMapper.readTree(first.body());
        assertEquals("openrouter/free", firstBody.get("model").asString());
        assertEquals(4, firstBody.get("tools").size());

        JsonNode secondMessages = jsonMapper.readTree(STUB.received().get(1).body()).get("messages");
        JsonNode toolMessage = secondMessages.get(secondMessages.size() - 1);
        assertEquals("tool", toolMessage.get("role").asString());
        assertEquals("call_9", toolMessage.get("tool_call_id").asString());
        JsonNode result = jsonMapper.readTree(toolMessage.get("content").asString());
        assertEquals("SUCCESS", result.get("status").asString());
        assertEquals("Anil Rao", result.get("data").get("people").get(0).get("fullName").asString());

        String generatedBy = jdbc.queryForObject("SELECT generated_by FROM assistant_messages WHERE uuid = ?::uuid",
                String.class, text(exchange.get("reply"), "uuid"));
        assertEquals("openrouter:" + SERVED, generatedBy);
    }

    @Test
    void anUnavailableProviderGivesTheFallbackReplyWithoutDetails() {
        String caregiver = caregiverToken("Down Carer");
        String conversation = text(expect(201, "POST", "/api/assistant/conversations", caregiver, null).body(), "uuid");

        STUB.enqueue(Scripted.status(503), Scripted.status(503));
        JsonNode exchange = expect(200, "POST", "/api/assistant/conversations/" + conversation + "/messages",
                caregiver, "{\"content\":\"What alerts are there?\"}").body();
        assertEquals(AssistantResponder.FALLBACK_REPLY, text(exchange.get("reply"), "content"));
        assertEquals(2, STUB.received().size(), "retried once");
        assertFalse(exchange.toString().contains("503"));

        STUB.reset();
        STUB.enqueue(Scripted.status(401));
        JsonNode rejected = expect(200, "POST", "/api/assistant/conversations/" + conversation + "/messages",
                caregiver, "{\"content\":\"Hello\"}").body();
        assertEquals(AssistantResponder.FALLBACK_REPLY, text(rejected.get("reply"), "content"));
        assertEquals(1, STUB.received().size(), "a refused key is not retried");
        assertTrue(text(rejected.get("userMessage"), "content").equals("Hello"));
    }
}
