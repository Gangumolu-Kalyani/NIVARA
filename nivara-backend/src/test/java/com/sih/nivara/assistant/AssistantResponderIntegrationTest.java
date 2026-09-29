package com.sih.nivara.assistant;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.llm.LlmChatRequest;
import com.sih.nivara.assistant.llm.LlmClient;
import com.sih.nivara.assistant.llm.LlmException;
import com.sih.nivara.assistant.llm.LlmMessage;
import com.sih.nivara.assistant.llm.LlmResponse;
import com.sih.nivara.assistant.llm.ToolCall;
import com.sih.nivara.assistant.service.AssistantResponder;
import com.sih.nivara.assistant.tool.AssistantTool;
import com.sih.nivara.assistant.tool.NoArguments;
import com.sih.nivara.assistant.tool.ToolInvocation;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tool-calling loop end to end: the HTTP API, AssistantService, AssistantResponder, the real
 * ToolExecutor and tools, and a scripted model standing in for OpenRouter.
 */
@TestPropertySource(properties = "nivara.assistant.llm.exchange-timeout=PT3S")
class AssistantResponderIntegrationTest extends EmbeddedPostgresIntegrationTest {

    private static final String CONVERSATIONS = "/api/assistant/conversations";

    /** A model whose every answer is scripted, and which records what it was asked. */
    static final class ScriptedLlmClient implements LlmClient {
        interface Step {
            LlmResponse answer(LlmChatRequest request) throws LlmException;
        }

        final ConcurrentLinkedQueue<Step> script = new ConcurrentLinkedQueue<>();
        final List<LlmChatRequest> requests = new CopyOnWriteArrayList<>();
        Step whenScriptRunsOut = request -> new LlmResponse.FinalText("(unscripted)", "scripted:model");

        @Override
        public LlmResponse chat(LlmChatRequest request) throws LlmException {
            requests.add(request);
            Step step = script.poll();
            return (step != null ? step : whenScriptRunsOut).answer(request);
        }
    }

    /**
     * A tool that would change data: it must never run, as no confirmation flow exists yet.
     * Caregiver-only, so patients still see exactly their four tools.
     */
    static final class FakeWriteTool implements AssistantTool<NoArguments> {
        static final AtomicBoolean EXECUTED = new AtomicBoolean();

        public String name() { return "fake_write_tool"; }
        public String description() { return "Changes something"; }
        public Class<NoArguments> argumentsType() { return NoArguments.class; }
        public Map<String, Object> argumentProperties() { return Map.of(); }
        public Set<AssistantMode> modes() { return Set.of(AssistantMode.CAREGIVER); }
        public AccessLevel requiredAccess() { return AccessLevel.EDITOR; }
        public boolean requiresConfirmation() { return true; }

        public Object execute(ToolInvocation<NoArguments> invocation) {
            EXECUTED.set(true);
            return "changed";
        }
    }

    @TestConfiguration
    static class ScriptedModelConfiguration {
        static final ScriptedLlmClient MODEL = new ScriptedLlmClient();

        @Bean
        @Primary
        LlmClient scriptedLlmClient() {
            return MODEL;
        }

        @Bean
        FakeWriteTool fakeWriteTool() {
            return new FakeWriteTool();
        }
    }

    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;

    private final ScriptedLlmClient model = ScriptedModelConfiguration.MODEL;

    @BeforeEach
    void resetModel() {
        model.script.clear();
        model.requests.clear();
        model.whenScriptRunsOut = request -> new LlmResponse.FinalText("(unscripted)", "scripted:model");
        FakeWriteTool.EXECUTED.set(false);
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static LlmResponse.ToolCalls calls(String... idNameArgs) {
        List<ToolCall> calls = new ArrayList<>();
        for (int i = 0; i < idNameArgs.length; i += 3) {
            calls.add(new ToolCall(idNameArgs[i], idNameArgs[i + 1], idNameArgs[i + 2]));
        }
        return new LlmResponse.ToolCalls(calls, null, "scripted:model");
    }

    private void script(LlmResponse... responses) {
        for (LlmResponse response : responses) {
            model.script.add(request -> response);
        }
    }

    private JsonNode say(String token, String conversation, String content) {
        return expect(200, "POST", CONVERSATIONS + "/" + conversation + "/messages", token,
                "{\"content\":\"" + content + "\"}").body();
    }

    private String reply(JsonNode exchange) {
        return text(exchange.get("reply"), "content");
    }

    /** The tool results the model was given in its n-th call (0-based), parsed, by call id. */
    private Map<String, JsonNode> toolResults(int call) {
        Map<String, JsonNode> results = new java.util.LinkedHashMap<>();
        for (LlmMessage message : model.requests.get(call).messages()) {
            if (message instanceof LlmMessage.ToolResult result) {
                results.put(result.toolCallId(), jsonMapper.readTree(result.content()));
            }
        }
        return results;
    }

    private static String errorCode(JsonNode result) {
        return result.get("error").isNull() ? null : text(result.get("error"), "code");
    }

    private static String systemPrompt(LlmChatRequest request) {
        return ((LlmMessage.System) request.messages().get(0)).content();
    }

    /** A caregiver, their patient with two people, and the patient's own device token. */
    private record Seed(String caregiver, String patient, String patientToken) {
    }

    private Seed seed(String caregiverName, String patientJson) {
        String caregiver = caregiverToken(caregiverName);
        String patient = text(expect(201, "POST", "/api/patients", caregiver, patientJson).body(), "uuid");
        expect(201, "POST", "/api/patients/" + patient + "/people", caregiver,
                "{\"fullName\":\"Anil Rao\",\"calledAs\":\"Anil\",\"relationship\":\"SON\"}");
        expect(201, "POST", "/api/patients/" + patient + "/people", caregiver,
                "{\"fullName\":\"Meena Rao\",\"relationship\":\"DAUGHTER\"}");
        return new Seed(caregiver, patient, patientToken(caregiver, patient));
    }

    // ---- the loop ----------------------------------------------------------------------------

    @Test
    void aToolCallRunsThroughTheExecutorAndItsResultGoesBackToTheModel() {
        Seed seed = seed("Ravi Kumar", "{\"fullName\":\"Lakshmi Rao\",\"preferredName\":\"Amma\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        script(calls("call_1", "get_people", "{\"name\":\"anil\"}"),
                new LlmResponse.FinalText("Anil is your son.", "scripted:model"));

        JsonNode exchange = say(seed.patientToken(), conversation, "Who is Anil?");
        assertEquals("Anil is your son.", reply(exchange));

        // First call: system prompt, the user's message, the patient's four tools, tools allowed
        LlmChatRequest first = model.requests.get(0);
        String prompt = systemPrompt(first);
        assertTrue(prompt.contains("Say one thing at a time"), prompt);
        assertTrue(prompt.contains("Do not give medical advice"), prompt);
        assertTrue(prompt.contains("Amma"), "the authorized patient's preferred name");
        assertTrue(prompt.contains("Always reply in English"), prompt);
        assertTrue(prompt.contains("The current local date and time is"), prompt);
        assertInstanceOf(LlmMessage.User.class, first.messages().get(first.messages().size() - 1));
        assertEquals(4, first.tools().size());
        assertEquals(LlmChatRequest.ToolChoice.AUTO, first.toolChoice());

        // Second call: the model's tool call, then its result, matched by id
        LlmChatRequest second = model.requests.get(1);
        LlmMessage.Assistant asked = assertInstanceOf(LlmMessage.Assistant.class,
                second.messages().get(second.messages().size() - 2));
        assertEquals("call_1", asked.toolCalls().get(0).id());
        JsonNode result = toolResults(1).get("call_1");
        assertEquals("SUCCESS", text(result, "status"));
        assertEquals("get_people", text(result, "tool"));
        assertEquals("Anil Rao", text(result.get("data").get("people").get(0), "fullName"));
        assertEquals(1, result.get("data").get("totalMatching").asInt());

        // Stored with the model that produced it
        String generatedBy = jdbc.queryForObject("SELECT generated_by FROM assistant_messages WHERE uuid = ?::uuid",
                String.class, text(exchange.get("reply"), "uuid"));
        assertEquals("scripted:model", generatedBy);
    }

    @Test
    void severalToolCallsInOneRoundAndAcrossRounds() {
        Seed seed = seed("Asha Menon", "{\"fullName\":\"Patient Rounds\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        script(calls("a1", "get_people", "{}", "a2", "search_memories", "{}"),
                calls("b1", "get_today_reminders", "{}"),
                new LlmResponse.FinalText("Here is your day.", "scripted:model"));

        assertEquals("Here is your day.", reply(say(seed.patientToken(), conversation, "Tell me about today")));
        assertEquals(3, model.requests.size());

        Map<String, JsonNode> round1 = toolResults(1);
        assertEquals(List.of("a1", "a2"), List.copyOf(round1.keySet()), "one result per call, in order");
        assertEquals("SUCCESS", text(round1.get("a1"), "status"));
        assertEquals("search_memories", text(round1.get("a2"), "tool"));
        Map<String, JsonNode> all = toolResults(2);
        assertEquals(List.of("a1", "a2", "b1"), List.copyOf(all.keySet()));
        assertEquals("get_today_reminders", text(all.get("b1"), "tool"));
    }

    @Test
    void badArgumentsComeBackAsStructuredErrors() {
        Seed seed = seed("Meera Iyer", "{\"fullName\":\"Patient Arguments\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        script(calls("c1", "get_people", "not json at all",
                        "c2", "get_people", "{\"limit\":0}",
                        "c3", "get_people", "{\"bogus\":1}",
                        "c4", "no_such_tool", "{}"),
                new LlmResponse.FinalText("Sorry, I could not look that up.", "scripted:model"));

        say(seed.patientToken(), conversation, "Who is there?");
        Map<String, JsonNode> results = toolResults(1);
        assertEquals("INVALID_ARGUMENTS", errorCode(results.get("c1")));
        assertEquals("INVALID_ARGUMENTS", errorCode(results.get("c2")));
        assertEquals("INVALID_ARGUMENTS", errorCode(results.get("c3")));
        assertEquals("UNKNOWN_TOOL", errorCode(results.get("c4")));
        results.values().forEach(r -> assertTrue(r.get("data").isNull()));
    }

    // ---- authorization -----------------------------------------------------------------------

    @Test
    void theModelCannotTakeAPatientToAnotherPatientsData() {
        Seed seed = seed("Kiran Rao", "{\"fullName\":\"Sita Rao\"}");
        String other = createPatient(seed.caregiver(), "Other Patient");
        expect(201, "POST", "/api/patients/" + other + "/people", seed.caregiver(),
                "{\"fullName\":\"Secret Person\",\"relationship\":\"FRIEND\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        script(calls("d1", "get_people", "{\"patientUuid\":\"" + other + "\"}",
                        "d2", "search_memories", "{\"patientUuid\":\"" + other + "\"}",
                        "d3", "get_alerts", "{}",
                        "d4", "fake_write_tool", "{}"),
                new LlmResponse.FinalText("I can only help with your own things.", "scripted:model"));

        say(seed.patientToken(), conversation, "Show me everyone");
        Map<String, JsonNode> results = toolResults(1);
        assertEquals("FORBIDDEN", errorCode(results.get("d1")));
        assertEquals("FORBIDDEN", errorCode(results.get("d2")));
        assertEquals("NOT_ALLOWED", errorCode(results.get("d3")), "caregiver-only tool");
        assertEquals("CONFIRMATION_REQUIRED", errorCode(results.get("d4")));
        assertFalse(FakeWriteTool.EXECUTED.get(), "a tool needing confirmation never runs");
        assertFalse(model.requests.get(1).messages().toString().contains("Secret Person"),
                "nothing about the other patient reached the model");
    }

    @Test
    void aCaregiversToolCallsStayWithinTheirAccess() {
        Seed seed = seed("Farah Khan", "{\"fullName\":\"Salma Khan\"}");
        String secondPatient = createPatient(seed.caregiver(), "Second Patient");

        // A conversation about one patient: tools default to it and cannot switch to another
        String pinned = text(expect(201, "POST", CONVERSATIONS, seed.caregiver(),
                "{\"patientUuid\":\"" + seed.patient() + "\"}").body(), "uuid");
        script(calls("e1", "get_people", "{}",
                        "e2", "get_people", "{\"patientUuid\":\"" + secondPatient + "\"}",
                        "e3", "get_alerts", "{}"),
                new LlmResponse.FinalText("Two people are listed.", "scripted:model"));
        say(seed.caregiver(), pinned, "Who is in her life?");
        assertTrue(systemPrompt(model.requests.get(0)).contains("Be concise and factual"), "caregiver prompt");
        assertTrue(systemPrompt(model.requests.get(0)).contains("Tools already know which patient"));
        assertEquals(7, model.requests.get(0).tools().size(), "the six tools and the fake write tool");
        Map<String, JsonNode> results = toolResults(1);
        assertEquals(2, results.get("e1").get("data").get("people").size());
        assertEquals("FORBIDDEN", errorCode(results.get("e2")));
        assertEquals("SUCCESS", text(results.get("e3"), "status"));

        // A general conversation must name the patient, and only a reachable one
        model.requests.clear();
        String stranger = caregiverToken("Stranger Carer");
        String general = text(expect(201, "POST", CONVERSATIONS, stranger, null).body(), "uuid");
        script(calls("f1", "get_people", "{}",
                        "f2", "get_people", "{\"patientUuid\":\"" + seed.patient() + "\"}"),
                new LlmResponse.FinalText("I cannot see that patient.", "scripted:model"));
        say(stranger, general, "Who is in Salma's life?");
        assertTrue(systemPrompt(model.requests.get(0)).contains("No patient has been chosen"));
        Map<String, JsonNode> strangers = toolResults(1);
        assertEquals("PATIENT_REQUIRED", errorCode(strangers.get("f1")));
        assertEquals("NOT_FOUND", errorCode(strangers.get("f2")));
        assertFalse(model.requests.get(1).messages().toString().contains("Anil"));
    }

    // ---- limits and failures -----------------------------------------------------------------

    @Test
    void afterFourRoundsTheModelMustAnswerWithoutTools() {
        Seed seed = seed("Nisha Pillai", "{\"fullName\":\"Raman Pillai\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        model.whenScriptRunsOut = request -> request.toolChoice() == LlmChatRequest.ToolChoice.NONE
                ? new LlmResponse.FinalText("Let me stop there.", "scripted:model")
                : calls("r" + request.messages().size(), "get_people", "{}");

        assertEquals("Let me stop there.", reply(say(seed.patientToken(), conversation, "Keep looking")));
        assertEquals(5, model.requests.size(), "4 rounds with tools, then 1 without");
        for (int i = 0; i < 4; i++) {
            assertEquals(LlmChatRequest.ToolChoice.AUTO, model.requests.get(i).toolChoice());
        }
        assertEquals(LlmChatRequest.ToolChoice.NONE, model.requests.get(4).toolChoice());
        assertEquals(4, model.requests.get(4).tools().size(), "tools are still sent, as OpenRouter requires");

        // A model that still asks for tools gets the fallback
        model.requests.clear();
        model.whenScriptRunsOut = request -> calls("x" + request.messages().size(), "get_people", "{}");
        assertEquals(AssistantResponder.FALLBACK_REPLY, reply(say(seed.patientToken(), conversation, "Again")));
        assertEquals(5, model.requests.size());
    }

    @Test
    void atMostFiveToolCallsRunPerRound() {
        Seed seed = seed("Vikram Shah", "{\"fullName\":\"Limit Patient\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        String[] seven = new String[21];
        for (int i = 0; i < 7; i++) {
            seven[i * 3] = "g" + i;
            seven[i * 3 + 1] = "get_people";
            seven[i * 3 + 2] = "{}";
        }
        script(calls(seven), new LlmResponse.FinalText("Done.", "scripted:model"));

        say(seed.patientToken(), conversation, "Look seven times");
        Map<String, JsonNode> results = toolResults(1);
        assertEquals(7, results.size(), "every call gets a result");
        for (int i = 0; i < 5; i++) {
            assertEquals("SUCCESS", text(results.get("g" + i), "status"));
        }
        assertEquals("CALL_LIMIT_EXCEEDED", errorCode(results.get("g5")));
        assertEquals("CALL_LIMIT_EXCEEDED", errorCode(results.get("g6")));
    }

    @Test
    void aFailingModelGetsTheFallbackReplyAndTheMessageIsKept() {
        Seed seed = seed("Owner Carer", "{\"fullName\":\"Fallback Patient\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        model.script.add(request -> {
            throw new LlmException(LlmException.Reason.RATE_LIMITED, "Model provider rate limit (HTTP 429)");
        });

        JsonNode exchange = say(seed.patientToken(), conversation, "What is my next reminder?");
        assertEquals(AssistantResponder.FALLBACK_REPLY, reply(exchange));
        assertFalse(reply(exchange).contains("429"), "no provider details reach the user");
        List<Map<String, Object>> stored = jdbc.queryForList("""
                SELECT m.sender, m.content, m.generated_by FROM assistant_messages m
                JOIN assistant_conversations c ON c.id = m.conversation_id
                WHERE c.uuid = ?::uuid ORDER BY m.sequence_number""", conversation);
        assertEquals("What is my next reminder?", stored.get(0).get("content"));
        assertEquals("fallback", stored.get(1).get("generated_by"));

        // An empty answer is a failure too; an overlong one is cut
        script(new LlmResponse.FinalText("   ", "scripted:model"));
        assertEquals(AssistantResponder.FALLBACK_REPLY, reply(say(seed.patientToken(), conversation, "Hello?")));
        script(new LlmResponse.FinalText("x".repeat(5000), "scripted:model"));
        assertEquals(4000, reply(say(seed.patientToken(), conversation, "Tell me everything")).length());
    }

    @Test
    void anUnexpectedClientErrorAlsoGetsTheFallbackNotAnError() {
        Seed seed = seed("Crash Carer", "{\"fullName\":\"Crash Patient\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");

        model.script.add(request -> {
            throw new IllegalStateException("secret-detail-that-must-not-leak");
        });
        JsonNode exchange = say(seed.patientToken(), conversation, "Hello?");
        assertEquals(AssistantResponder.FALLBACK_REPLY, reply(exchange));
        assertFalse(exchange.toString().contains("secret-detail"));

        model.script.add(request -> null);
        assertEquals(AssistantResponder.FALLBACK_REPLY, reply(say(seed.patientToken(), conversation, "Still there?")));

        List<Map<String, Object>> stored = jdbc.queryForList("""
                SELECT m.sender, m.generated_by FROM assistant_messages m
                JOIN assistant_conversations c ON c.id = m.conversation_id
                WHERE c.uuid = ?::uuid ORDER BY m.sequence_number""", conversation);
        assertEquals(4, stored.size(), "each message stored once, each answered by the fallback");
        assertEquals("fallback", stored.get(1).get("generated_by"));
        assertEquals("fallback", stored.get(3).get("generated_by"));
    }

    @Test
    void theWholeExchangeHasADeadline() {
        Seed seed = seed("Slow Carer", "{\"fullName\":\"Slow Patient\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(), null).body(), "uuid");
        // The exchange may take 3 s here; the first round alone takes 2.5 s
        model.script.add(request -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return calls("s1", "get_people", "{}");
        });

        assertEquals(AssistantResponder.FALLBACK_REPLY, reply(say(seed.patientToken(), conversation, "Take your time")));
        assertEquals(1, model.requests.size(), "no second round was started without enough time");
    }

    @Test
    void theModelSeesTheRecentConversationInTheChosenLanguage() {
        Seed seed = seed("Lang Carer", "{\"fullName\":\"Language Patient\"}");
        String conversation = text(expect(201, "POST", CONVERSATIONS, seed.patientToken(),
                "{\"languageCode\":\"hi-IN\"}").body(), "uuid");
        script(new LlmResponse.FinalText("Namaste.", "scripted:model"), new LlmResponse.FinalText("Theek hai.", "scripted:model"));

        say(seed.patientToken(), conversation, "Hello");
        say(seed.patientToken(), conversation, "How are you?");

        LlmChatRequest second = model.requests.get(1);
        assertTrue(systemPrompt(second).contains("Always reply in Hindi (language code hi-IN)"), systemPrompt(second));
        List<LlmMessage> turns = second.messages();
        assertEquals(List.of("System", "User", "Assistant", "User"),
                turns.stream().map(m -> m.getClass().getSimpleName()).toList());
        assertEquals("Namaste.", ((LlmMessage.Assistant) turns.get(2)).content());
        assertFalse(systemPrompt(second).contains(seed.patient()), "no identifiers in the prompt");
    }
}
