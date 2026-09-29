package com.sih.nivara.assistant.tool;

import com.sih.nivara.assistant.entity.enums.AssistantMode;
import com.sih.nivara.assistant.llm.LlmClient;
import com.sih.nivara.assistant.llm.LlmReply;
import com.sih.nivara.assistant.llm.LlmRequest;
import com.sih.nivara.assistant.llm.PlaceholderLlmClient;
import com.sih.nivara.assistant.service.AssistantContext;
import com.sih.nivara.assistant.service.AssistantContextResolver;
import com.sih.nivara.security.AccountJwtAuthenticationConverter;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The tool framework end to end: real services, a real database, and callers authenticated with
 * real access tokens, exactly as they would be inside an HTTP request.
 */
class ToolExecutorIntegrationTest extends EmbeddedPostgresIntegrationTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Records what the assistant offers a model, and answers like the placeholder. */
    @TestConfiguration
    static class RecordingLlmConfiguration {
        static final AtomicReference<LlmRequest> LAST = new AtomicReference<>();

        @Bean
        @Primary
        LlmClient recordingLlmClient() {
            return request -> {
                LAST.set(request);
                return new LlmReply(PlaceholderLlmClient.REPLY, PlaceholderLlmClient.GENERATED_BY);
            };
        }
    }

    @Autowired ToolExecutor executor;
    @Autowired AssistantToolRegistry registry;
    @Autowired AssistantContextResolver contextResolver;
    @Autowired PatientAccessService patientAccessService;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired AccountJwtAuthenticationConverter accountConverter;
    @Autowired JsonMapper jsonMapper;
    @Autowired JdbcTemplate jdbc;

    // ---- helpers -----------------------------------------------------------------------------

    /** Runs the action as the account behind this token, authenticated like an HTTP request. */
    private <T> T as(String token, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(accountConverter.convert(jwtDecoder.decode(token)));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** The context a new conversation would have, for this caller and optional patient. */
    private AssistantContext context(String token, String patientUuid) {
        return as(token, () -> contextResolver.forNewConversation(patientAccessService.requireCaller(),
                patientUuid == null ? null : UUID.fromString(patientUuid), null));
    }

    /** Runs a tool as this caller and answers the result as JSON, the shape a model would see. */
    private JsonNode run(String token, AssistantContext context, String tool, String arguments) {
        ToolResult result = as(token, () -> executor.execute(context, tool,
                arguments == null ? null : jsonMapper.readTree(arguments)));
        return jsonMapper.valueToTree(result);
    }

    private static JsonNode data(JsonNode result) {
        assertEquals("SUCCESS", text(result, "status"), result.toString());
        assertTrue(result.get("error").isNull(), result.toString());
        return result.get("data");
    }

    private static void assertError(JsonNode result, ToolErrorCode code) {
        assertEquals("ERROR", text(result, "status"), result.toString());
        assertTrue(result.get("data").isNull(), result.toString());
        assertEquals(code.name(), text(result.get("error"), "code"), result.toString());
        assertNotNull(text(result.get("error"), "message"));
    }

    /** A patient with two people, two memories and two of today's reminders, one already due. */
    private record Seed(String caregiver, String patient, String patientToken) {
    }

    private Seed seed(String caregiverName, String patientName) {
        LocalTime now = LocalTime.now(IST);
        assumeTrue(now.isAfter(LocalTime.of(0, 15)) && now.isBefore(LocalTime.of(23, 45)),
                "reminder timings need to stay within today in India");

        String caregiver = caregiverToken(caregiverName);
        String patient = createPatient(caregiver, patientName);
        String base = "/api/patients/" + patient;

        String anil = text(expect(201, "POST", base + "/people", caregiver,
                "{\"fullName\":\"Anil Rao\",\"calledAs\":\"Anil\",\"relationship\":\"SON\"}").body(), "uuid");
        String meena = text(expect(201, "POST", base + "/people", caregiver,
                "{\"fullName\":\"Meena Rao\",\"relationship\":\"DAUGHTER\",\"relationshipLabel\":\"eldest daughter\"}").body(), "uuid");
        LocalDate today = LocalDate.now(IST);
        expect(201, "POST", base + "/memories", caregiver, "{\"title\":\"Anil visited\",\"description\":\"Anil came "
                + "to see her and brought sweets.\",\"memoryType\":\"VISIT\",\"occurredOn\":\"" + today.minusDays(1)
                + "\",\"languageCode\":\"en\",\"source\":\"CAREGIVER\",\"peopleUuids\":[\"" + anil + "\"]}");
        expect(201, "POST", base + "/memories", caregiver, "{\"title\":\"Temple outing\",\"description\":\"Went to "
                + "the temple for the festival.\",\"memoryType\":\"OUTING\",\"occurredOn\":\"" + today.minusDays(7)
                + "\",\"languageCode\":\"en\",\"source\":\"CAREGIVER\",\"peopleUuids\":[\"" + meena + "\"]}");

        // Medicine was due five minutes ago: backdate its schedule so today's slot exists
        String medicine = text(expect(201, "POST", base + "/reminders", caregiver,
                "{\"category\":\"MEDICINE\",\"title\":\"Morning Medicine\",\"scheduledTime\":\""
                        + now.minusMinutes(5).format(HH_MM_SS) + "\"}").body(), "uuid");
        jdbc.update("UPDATE reminders SET effective_from = now() - interval '1 day' WHERE uuid = ?::uuid", medicine);
        expect(201, "POST", base + "/reminders", caregiver,
                "{\"category\":\"HYDRATION\",\"title\":\"Glass of water\",\"scheduledTime\":\""
                        + now.plusMinutes(10).format(HH_MM_SS) + "\"}");

        return new Seed(caregiver, patient, patientToken(caregiver, patient));
    }

    // ---- registry ----------------------------------------------------------------------------

    @Test
    void theSixReadOnlyToolsAreRegisteredWithoutConfirmation() {
        assertEquals(List.of("get_alerts", "get_daily_summary", "get_next_reminder", "get_people",
                "get_today_reminders", "search_memories"), registry.all().stream().map(AssistantTool::name).toList());
        registry.all().forEach(tool -> assertFalse(tool.requiresConfirmation(), tool.name()));

        assertEquals(Set.of("get_today_reminders", "get_next_reminder", "get_people", "search_memories"),
                Set.copyOf(registry.toolsFor(AssistantMode.PATIENT).stream().map(ToolDefinition::name).toList()));
        assertEquals(6, registry.toolsFor(AssistantMode.CAREGIVER).size());
        registry.toolsFor(AssistantMode.PATIENT).forEach(definition -> {
            assertFalse(definition.requiresConfirmation());
            assertFalse(((java.util.Map<?, ?>) definition.parametersSchema().get("properties"))
                    .containsKey(AssistantToolRegistry.PATIENT_UUID), definition.name());
        });
    }

    @Test
    void theAssistantOffersTheToolsOfTheConversationsMode() {
        String caregiver = caregiverToken("Offer Carer");
        String patient = createPatient(caregiver, "Offer Patient");
        String patientToken = patientToken(caregiver, patient);

        String patientConversation = text(expect(201, "POST", "/api/assistant/conversations", patientToken, null).body(), "uuid");
        JsonNode exchange = expect(200, "POST", "/api/assistant/conversations/" + patientConversation + "/messages",
                patientToken, "{\"content\":\"What is my next reminder?\"}").body();
        assertEquals(PlaceholderLlmClient.REPLY, text(exchange.get("reply"), "content"));
        LlmRequest offered = RecordingLlmConfiguration.LAST.get();
        assertEquals(AssistantMode.PATIENT, offered.mode());
        assertEquals(Set.of("get_today_reminders", "get_next_reminder", "get_people", "search_memories"),
                Set.copyOf(offered.tools().stream().map(ToolDefinition::name).toList()));

        String caregiverConversation = text(expect(201, "POST", "/api/assistant/conversations", caregiver, null).body(), "uuid");
        expect(200, "POST", "/api/assistant/conversations/" + caregiverConversation + "/messages",
                caregiver, "{\"content\":\"What alerts are there?\"}");
        assertEquals(6, RecordingLlmConfiguration.LAST.get().tools().size());
    }

    // ---- patient -----------------------------------------------------------------------------

    @Test
    void aPatientReadsTheirOwnRemindersPeopleAndMemories() {
        Seed seed = seed("Ravi Kumar", "Lakshmi Rao");
        AssistantContext context = context(seed.patientToken(), null);
        assertEquals(AssistantMode.PATIENT, context.mode());

        JsonNode today = data(run(seed.patientToken(), context, "get_today_reminders", null));
        assertEquals(LocalDate.now(IST).toString(), text(today, "date"));
        assertEquals(2, today.get("reminders").size());
        assertEquals("Morning Medicine", text(today.get("reminders").get(0), "reminderTitle"));
        assertEquals("Glass of water", text(today.get("reminders").get(1), "reminderTitle"));
        assertFalse(today.get("truncated").asBoolean());

        JsonNode next = data(run(seed.patientToken(), context, "get_next_reminder", "{}"));
        assertEquals("Glass of water", text(next.get("next"), "reminderTitle"));
        assertEquals(1, next.get("dueNow").size());
        assertEquals("Morning Medicine", text(next.get("dueNow").get(0), "reminderTitle"));

        JsonNode people = data(run(seed.patientToken(), context, "get_people", null));
        assertEquals(2, people.get("totalMatching").asInt());
        JsonNode anil = data(run(seed.patientToken(), context, "get_people", "{\"name\":\"ANIL\"}"));
        assertEquals(1, anil.get("people").size());
        assertEquals("Anil Rao", text(anil.get("people").get(0), "fullName"));
        assertEquals("SON", text(anil.get("people").get(0), "relationship"));
        JsonNode eldest = data(run(seed.patientToken(), context, "get_people", "{\"name\":\"eldest\"}"));
        assertEquals("Meena Rao", text(eldest.get("people").get(0), "fullName"));
        JsonNode daughters = data(run(seed.patientToken(), context, "get_people", "{\"relationship\":\"DAUGHTER\"}"));
        assertEquals(1, daughters.get("people").size());

        JsonNode all = data(run(seed.patientToken(), context, "search_memories", "{}"));
        assertEquals(2, all.get("totalMatching").asInt());
        assertEquals("Anil visited", text(all.get("memories").get(0), "title"), "most recent first");
        JsonNode sweets = data(run(seed.patientToken(), context, "search_memories", "{\"query\":\"Sweets\"}"));
        assertEquals("Anil visited", text(sweets.get("memories").get(0), "title"));
        JsonNode withMeena = data(run(seed.patientToken(), context, "search_memories", "{\"personName\":\"meena\"}"));
        assertEquals("Temple outing", text(withMeena.get("memories").get(0), "title"));
        JsonNode recent = data(run(seed.patientToken(), context, "search_memories",
                "{\"fromDate\":\"" + LocalDate.now(IST).minusDays(2) + "\"}"));
        assertEquals(1, recent.get("totalMatching").asInt());
        JsonNode limited = data(run(seed.patientToken(), context, "search_memories", "{\"limit\":1}"));
        assertEquals(1, limited.get("memories").size());
        assertEquals(2, limited.get("totalMatching").asInt());
    }

    @Test
    void aPatientCanOnlyEverReachTheirOwnRecord() {
        Seed seed = seed("Asha Menon", "Patient A");
        String other = createPatient(seed.caregiver(), "Patient B");
        expect(201, "POST", "/api/patients/" + other + "/people", seed.caregiver(),
                "{\"fullName\":\"Someone Else\",\"relationship\":\"FRIEND\"}");
        String otherToken = patientToken(seed.caregiver(), other);
        AssistantContext context = context(seed.patientToken(), null);

        // Naming another patient is refused; naming themselves is fine
        assertError(run(seed.patientToken(), context, "get_people", "{\"patientUuid\":\"" + other + "\"}"),
                ToolErrorCode.FORBIDDEN);
        assertError(run(seed.patientToken(), context, "search_memories", "{\"patientUuid\":\"" + other + "\"}"),
                ToolErrorCode.FORBIDDEN);
        JsonNode own = data(run(seed.patientToken(), context, "get_people", "{\"patientUuid\":\"" + seed.patient() + "\"}"));
        assertEquals(2, own.get("people").size());
        assertFalse(own.toString().contains("Someone Else"));

        // Caregiver-only tools
        assertError(run(seed.patientToken(), context, "get_alerts", null), ToolErrorCode.NOT_ALLOWED);
        assertError(run(seed.patientToken(), context, "get_daily_summary", null), ToolErrorCode.NOT_ALLOWED);

        // Patient A's conversation run under patient B's token
        assertError(run(otherToken, context, "get_people", null), ToolErrorCode.FORBIDDEN);
    }

    // ---- caregiver ---------------------------------------------------------------------------

    @Test
    void aCaregiverNeedsAccessToThePatientOnEveryCall() {
        Seed seed = seed("Meera Iyer", "Gopal Iyer");
        String patientArg = "{\"patientUuid\":\"" + seed.patient() + "\"}";

        // A general conversation must name the patient
        AssistantContext general = context(seed.caregiver(), null);
        assertError(run(seed.caregiver(), general, "get_people", null), ToolErrorCode.PATIENT_REQUIRED);
        assertEquals(2, data(run(seed.caregiver(), general, "get_people", patientArg)).get("people").size());

        // A conversation about one patient stays about that patient, even one the caregiver can reach
        String secondPatient = createPatient(seed.caregiver(), "Second Patient");
        AssistantContext pinned = context(seed.caregiver(), seed.patient());
        assertEquals(2, data(run(seed.caregiver(), pinned, "get_people", null)).get("people").size());
        assertError(run(seed.caregiver(), pinned, "get_people", "{\"patientUuid\":\"" + secondPatient + "\"}"),
                ToolErrorCode.FORBIDDEN);

        // No access, or no such patient
        String stranger = caregiverToken("Stranger Carer");
        AssistantContext strangers = context(stranger, null);
        assertError(run(stranger, strangers, "get_people", patientArg), ToolErrorCode.NOT_FOUND);
        assertError(run(stranger, strangers, "get_alerts", patientArg), ToolErrorCode.NOT_FOUND);
        assertError(run(stranger, strangers, "get_people", "{\"patientUuid\":\"" + UUID.randomUUID() + "\"}"),
                ToolErrorCode.NOT_FOUND);

        // VIEWER is enough; revoking access takes effect on the very next call
        String viewer = caregiverToken("Viewer Carer");
        String viewerUuid = text(expect(200, "GET", "/api/auth/me", viewer, null).body(), "uuid");
        expect(201, "POST", "/api/patients/" + seed.patient() + "/caregivers", seed.caregiver(),
                "{\"caregiverUserUuid\":\"" + viewerUuid + "\",\"relationship\":\"SON\",\"accessLevel\":\"VIEWER\"}");
        AssistantContext viewers = context(viewer, seed.patient());
        assertEquals(2, data(run(viewer, viewers, "get_today_reminders", null)).get("reminders").size());
        expect(204, "DELETE", "/api/patients/" + seed.patient() + "/caregivers/" + viewerUuid, seed.caregiver(), null);
        assertError(run(viewer, viewers, "get_today_reminders", null), ToolErrorCode.NOT_FOUND);
    }

    @Test
    void aCaregiverReadsAlertsAndTheDailySummary() {
        Seed seed = seed("Kiran Rao", "Sita Rao");
        AssistantContext context = context(seed.caregiver(), seed.patient());

        assertEquals(0, data(run(seed.caregiver(), context, "get_alerts", null)).get("totalMatching").asInt());

        // The patient asks for help with the medicine that is due, through the existing reminder API
        JsonNode day = expect(200, "GET", "/api/patients/" + seed.patient() + "/daily-care", seed.caregiver(), null).body();
        JsonNode medicine = day.get(0);
        expect(200, "POST", "/api/reminders/" + text(medicine, "reminderUuid") + "/responses", seed.caregiver(),
                "{\"responseType\":\"NEED_HELP\",\"scheduledAt\":\"" + text(medicine, "scheduledAt") + "\"}");

        JsonNode alerts = data(run(seed.caregiver(), context, "get_alerts", "{\"status\":\"OPEN\"}"));
        assertEquals(1, alerts.get("totalMatching").asInt());
        JsonNode alert = alerts.get("alerts").get(0);
        assertEquals("HIGH", text(alert, "severity"));
        assertEquals("MEDICINE", text(alert, "category"));
        assertTrue(text(alert, "title").startsWith("Help requested"));
        assertEquals(0, data(run(seed.caregiver(), context, "get_alerts", "{\"status\":\"RESOLVED\"}"))
                .get("totalMatching").asInt());

        // The due medicine is no longer waiting once escalated
        JsonNode next = data(run(seed.caregiver(), context, "get_next_reminder", null));
        assertEquals(0, next.get("dueNow").size());
        assertEquals("Glass of water", text(next.get("next"), "reminderTitle"));

        JsonNode summary = data(run(seed.caregiver(), context, "get_daily_summary", null));
        assertEquals(LocalDate.now(IST).toString(), text(summary, "date"));
        String lines = summary.get("summaryLines").toString();
        assertTrue(lines.contains("Medicine: 0 of 1 confirmed."), lines);
        assertTrue(lines.contains("Asked for help once."), lines);
        JsonNode lastWeek = data(run(seed.caregiver(), context, "get_daily_summary",
                "{\"date\":\"" + LocalDate.now(IST).minusDays(7) + "\"}"));
        assertEquals(LocalDate.now(IST).minusDays(7).toString(), text(lastWeek, "date"));
        assertError(run(seed.caregiver(), context, "get_daily_summary",
                "{\"date\":\"" + LocalDate.now(IST).plusDays(2) + "\"}"), ToolErrorCode.INVALID_ARGUMENTS);
    }

    // ---- arguments and errors ----------------------------------------------------------------

    @Test
    void invalidCallsGetAStructuredError() {
        Seed seed = seed("Farah Khan", "Salma Khan");
        AssistantContext context = context(seed.caregiver(), seed.patient());
        String caregiver = seed.caregiver();

        JsonNode unknown = run(caregiver, context, "delete_everything", null);
        assertError(unknown, ToolErrorCode.UNKNOWN_TOOL);
        assertEquals("delete_everything", text(unknown, "tool"));

        String[][] invalid = {
                {"get_people", "[1, 2]"},
                {"get_people", "\"Anil\""},
                {"get_people", "{\"bogus\":true}"},
                {"get_people", "{\"limit\":0}"},
                {"get_people", "{\"limit\":51}"},
                {"get_people", "{\"limit\":\"many\"}"},
                {"get_people", "{\"relationship\":\"COUSIN\"}"},
                {"get_people", "{\"name\":\"" + "x".repeat(121) + "\"}"},
                {"get_people", "{\"patientUuid\":\"not-a-uuid\"}"},
                {"get_people", "{\"patientUuid\":42}"},
                {"search_memories", "{\"fromDate\":\"yesterday\"}"},
                {"search_memories", "{\"fromDate\":\"2026-09-10\",\"toDate\":\"2026-09-01\"}"},
                {"search_memories", "{\"limit\":21}"},
                {"get_alerts", "{\"status\":\"BOGUS\"}"},
                {"get_today_reminders", "{\"date\":\"2026-09-01\"}"},
        };
        for (String[] call : invalid) {
            JsonNode result = run(caregiver, context, call[0], call[1]);
            assertError(result, ToolErrorCode.INVALID_ARGUMENTS);
            assertEquals(call[0], text(result, "tool"));
        }

        assertEquals("Unknown argument 'bogus'",
                text(run(caregiver, context, "get_people", "{\"bogus\":true}").get("error"), "message"));
        assertTrue(text(run(caregiver, context, "get_people", "{\"limit\":0}").get("error"), "message").startsWith("limit:"));

        // A successful result has the same shape, with the tool name and no error
        JsonNode ok = run(caregiver, context, "get_people", "{\"limit\":1}");
        assertEquals("get_people", text(ok, "tool"));
        assertEquals(1, data(ok).get("people").size());
    }
}
