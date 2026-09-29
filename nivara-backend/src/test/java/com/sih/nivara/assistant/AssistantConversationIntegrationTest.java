package com.sih.nivara.assistant;

import com.sih.nivara.assistant.llm.PlaceholderLlmClient;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The assistant's conversation API: who may start, read and write which conversation, and what
 * is stored. The language model is the Phase 2 placeholder.
 */
class AssistantConversationIntegrationTest extends EmbeddedPostgresIntegrationTest {

    private static final String CONVERSATIONS = "/api/assistant/conversations";

    @Autowired
    JdbcTemplate jdbc;

    private JsonNode start(String token, String body) {
        return expect(201, "POST", CONVERSATIONS, token, body).body();
    }

    private JsonNode send(String token, String conversationUuid, String content) {
        return expect(200, "POST", CONVERSATIONS + "/" + conversationUuid + "/messages", token,
                "{\"content\":\"" + content + "\"}").body();
    }

    private String accountUuid(String token) {
        return text(expect(200, "GET", "/api/auth/me", token, null).body(), "uuid");
    }

    @Test
    void aPatientTalksToTheAssistantAboutThemselves() {
        String caregiver = caregiverToken("Ravi Kumar");
        String patient = createPatient(caregiver, "Lakshmi Rao");
        String patientToken = patientToken(caregiver, patient);

        Http created = expect(201, "POST", CONVERSATIONS, patientToken, null);
        JsonNode conversation = created.body();
        String uuid = text(conversation, "uuid");
        assertTrue(created.raw().headers().firstValue("Location").orElseThrow().endsWith(CONVERSATIONS + "/" + uuid));
        assertEquals("PATIENT", text(conversation, "mode"));
        assertEquals(patient, text(conversation, "patientUuid"));
        assertEquals("Lakshmi Rao", text(conversation, "patientName"));
        assertEquals("en", text(conversation, "languageCode"));
        assertEquals("ACTIVE", text(conversation, "status"));

        JsonNode exchange = send(patientToken, uuid, "  What do I need to do today?  ");
        assertEquals(uuid, text(exchange, "conversationUuid"));
        assertEquals("USER", text(exchange.get("userMessage"), "sender"));
        assertEquals("What do I need to do today?", text(exchange.get("userMessage"), "content"));
        assertEquals(1, exchange.get("userMessage").get("sequenceNumber").asInt());
        assertEquals("ASSISTANT", text(exchange.get("reply"), "sender"));
        assertEquals(PlaceholderLlmClient.REPLY, text(exchange.get("reply"), "content"));
        assertEquals(2, exchange.get("reply").get("sequenceNumber").asInt());

        send(patientToken, uuid, "Who is Anil?");

        JsonNode detail = expect(200, "GET", CONVERSATIONS + "/" + uuid, patientToken, null).body();
        assertEquals(uuid, text(detail.get("conversation"), "uuid"));
        JsonNode messages = detail.get("messages");
        assertEquals(4, messages.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, messages.get(i).get("sequenceNumber").asInt());
            assertEquals(i % 2 == 0 ? "USER" : "ASSISTANT", text(messages.get(i), "sender"));
        }
        assertEquals("Who is Anil?", text(messages.get(2), "content"));

        JsonNode list = expect(200, "GET", CONVERSATIONS, patientToken, null).body();
        assertEquals(1, list.size());
        assertEquals(uuid, text(list.get(0), "uuid"));
    }

    @Test
    void aPatientCanNeitherChooseNorReachAnotherPatient() {
        String caregiver = caregiverToken("Asha Menon");
        String patientA = createPatient(caregiver, "Patient A");
        String patientB = createPatient(caregiver, "Patient B");
        String tokenA = patientToken(caregiver, patientA);
        String tokenB = patientToken(caregiver, patientB);

        // A patient cannot start a conversation about somebody else, only about themselves
        expect(403, "POST", CONVERSATIONS, tokenA, "{\"patientUuid\":\"" + patientB + "\"}");
        assertEquals(patientA, text(start(tokenA, "{\"patientUuid\":\"" + patientA + "\"}"), "patientUuid"));

        String conversationA = text(start(tokenA, null), "uuid");
        send(tokenA, conversationA, "I took my medicine.");

        // Patient B cannot read, write to or close patient A's conversation
        expect(404, "GET", CONVERSATIONS + "/" + conversationA, tokenB, null);
        expect(404, "POST", CONVERSATIONS + "/" + conversationA + "/messages", tokenB, "{\"content\":\"Hello\"}");
        expect(404, "POST", CONVERSATIONS + "/" + conversationA + "/close", tokenB, null);
        assertEquals(0, expect(200, "GET", CONVERSATIONS, tokenB, null).body().size());

        // Nothing was added to A's conversation
        assertEquals(2, expect(200, "GET", CONVERSATIONS + "/" + conversationA, tokenA, null).body().get("messages").size());
    }

    @Test
    void aCaregiverTalksToTheAssistantWithOrWithoutAPatient() {
        String caregiver = caregiverToken("Meera Iyer");
        String patient = createPatient(caregiver, "Gopal Iyer");

        JsonNode general = start(caregiver, null);
        assertEquals("CAREGIVER", text(general, "mode"));
        assertNull(text(general, "patientUuid"));
        send(caregiver, text(general, "uuid"), "What alerts are there?");

        JsonNode about = start(caregiver, "{\"patientUuid\":\"" + patient + "\",\"languageCode\":\"hi-IN\"}");
        assertEquals("CAREGIVER", text(about, "mode"));
        assertEquals(patient, text(about, "patientUuid"));
        assertEquals("Gopal Iyer", text(about, "patientName"));
        assertEquals("hi-IN", text(about, "languageCode"));
        JsonNode exchange = send(caregiver, text(about, "uuid"), "How did Dad do today?");
        assertEquals(PlaceholderLlmClient.REPLY, text(exchange.get("reply"), "content"));

        // Most recently active first
        JsonNode list = expect(200, "GET", CONVERSATIONS, caregiver, null).body();
        assertEquals(2, list.size());
        assertEquals(text(about, "uuid"), text(list.get(0), "uuid"));

        expect(400, "POST", CONVERSATIONS, caregiver, "{\"languageCode\":\"english\"}");
    }

    @Test
    void aCaregiverNeedsAccessToThePatientOnEveryRequest() {
        String owner = caregiverToken("Owner Carer");
        String patient = createPatient(owner, "Protected Patient");

        // No access at all: the patient does not exist as far as the stranger can tell
        String stranger = caregiverToken("Stranger Carer");
        expect(404, "POST", CONVERSATIONS, stranger, "{\"patientUuid\":\"" + patient + "\"}");
        expect(404, "POST", CONVERSATIONS, stranger, "{\"patientUuid\":\"" + UUID.randomUUID() + "\"}");

        // VIEWER access is enough to talk about the patient
        String viewer = caregiverToken("Viewer Carer");
        String viewerUuid = accountUuid(viewer);
        expect(201, "POST", "/api/patients/" + patient + "/caregivers", owner,
                "{\"caregiverUserUuid\":\"" + viewerUuid + "\",\"relationship\":\"SON\",\"accessLevel\":\"VIEWER\"}");
        String conversation = text(start(viewer, "{\"patientUuid\":\"" + patient + "\"}"), "uuid");
        send(viewer, conversation, "Show me today's reminders.");
        String general = text(start(viewer, null), "uuid");

        // Access revoked: the conversation about that patient disappears at once
        expect(204, "DELETE", "/api/patients/" + patient + "/caregivers/" + viewerUuid, owner, null);
        expect(404, "GET", CONVERSATIONS + "/" + conversation, viewer, null);
        expect(404, "POST", CONVERSATIONS + "/" + conversation + "/messages", viewer, "{\"content\":\"Hello\"}");
        JsonNode list = expect(200, "GET", CONVERSATIONS, viewer, null).body();
        assertEquals(1, list.size());
        assertEquals(general, text(list.get(0), "uuid"));
        expect(200, "GET", CONVERSATIONS + "/" + general, viewer, null);

        // The patient's OWNER still cannot read a conversation another caregiver started
        expect(404, "GET", CONVERSATIONS + "/" + conversation, owner, null);
    }

    @Test
    void patientsAndCaregiversNeverSeeEachOthersConversations() {
        String caregiver = caregiverToken("Kiran Rao");
        String patient = createPatient(caregiver, "Sita Rao");
        String patientToken = patientToken(caregiver, patient);

        String patientsOwn = text(start(patientToken, null), "uuid");
        String caregiversAboutPatient = text(start(caregiver, "{\"patientUuid\":\"" + patient + "\"}"), "uuid");

        expect(404, "GET", CONVERSATIONS + "/" + patientsOwn, caregiver, null);
        expect(404, "POST", CONVERSATIONS + "/" + patientsOwn + "/messages", caregiver, "{\"content\":\"Hi\"}");
        expect(404, "GET", CONVERSATIONS + "/" + caregiversAboutPatient, patientToken, null);
        expect(404, "POST", CONVERSATIONS + "/" + caregiversAboutPatient + "/messages", patientToken, "{\"content\":\"Hi\"}");
    }

    @Test
    void requestsWithoutAValidTokenAreRejected() {
        String caregiver = caregiverToken("Token Carer");
        String conversation = text(start(caregiver, null), "uuid");

        for (String token : new String[] {null, "not-a-token"}) {
            expect(401, "POST", CONVERSATIONS, token, null);
            expect(401, "GET", CONVERSATIONS, token, null);
            expect(401, "GET", CONVERSATIONS + "/" + conversation, token, null);
            expect(401, "POST", CONVERSATIONS + "/" + conversation + "/messages", token, "{\"content\":\"Hi\"}");
            expect(401, "POST", CONVERSATIONS + "/" + conversation + "/close", token, null);
        }
    }

    @Test
    void messagesAreValidatedAndClosedConversationsTakeNoMore() {
        String caregiver = caregiverToken("Farah Khan");
        String conversation = text(start(caregiver, null), "uuid");

        expect(400, "POST", CONVERSATIONS + "/" + conversation + "/messages", caregiver, "{\"content\":\"   \"}");
        expect(400, "POST", CONVERSATIONS + "/" + conversation + "/messages", caregiver, "{}");
        expect(400, "POST", CONVERSATIONS + "/" + conversation + "/messages", caregiver,
                "{\"content\":\"" + "x".repeat(4001) + "\"}");
        expect(404, "GET", CONVERSATIONS + "/" + UUID.randomUUID(), caregiver, null);
        expect(400, "GET", CONVERSATIONS + "/not-a-uuid", caregiver, null);

        send(caregiver, conversation, "Hello");
        JsonNode closed = expect(200, "POST", CONVERSATIONS + "/" + conversation + "/close", caregiver, null).body();
        assertEquals("CLOSED", text(closed, "status"));
        assertNotNull(text(closed, "closedAt"));
        expect(200, "POST", CONVERSATIONS + "/" + conversation + "/close", caregiver, null);
        expect(409, "POST", CONVERSATIONS + "/" + conversation + "/messages", caregiver, "{\"content\":\"Still there?\"}");

        // History survives closing
        assertEquals(2, expect(200, "GET", CONVERSATIONS + "/" + conversation, caregiver, null).body().get("messages").size());
    }

    @Test
    void conversationsAndMessagesArePersistedForAuditing() {
        String caregiver = caregiverToken("Nisha Pillai");
        String patient = createPatient(caregiver, "Raman Pillai");
        String patientToken = patientToken(caregiver, patient);
        String patientAccount = accountUuid(patientToken);

        String conversation = text(start(patientToken, null), "uuid");
        send(patientToken, conversation, "My grandson visited me today.");

        Map<String, Object> stored = jdbc.queryForMap("""
                SELECT c.mode, c.language_code, c.status, o.uuid AS owner_uuid, p.uuid AS patient_uuid
                FROM assistant_conversations c
                JOIN app_users o ON o.id = c.owner_user_id
                JOIN patients p ON p.id = c.patient_id
                WHERE c.uuid = ?::uuid
                """, conversation);
        assertEquals("PATIENT", stored.get("mode"));
        assertEquals("ACTIVE", stored.get("status"));
        assertEquals(patientAccount, stored.get("owner_uuid").toString());
        assertEquals(patient, stored.get("patient_uuid").toString());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT m.sequence_number, m.sender, m.content, m.generated_by, u.uuid AS sender_uuid, m.created_at
                FROM assistant_messages m
                JOIN assistant_conversations c ON c.id = m.conversation_id
                LEFT JOIN app_users u ON u.id = m.sender_user_id
                WHERE c.uuid = ?::uuid
                ORDER BY m.sequence_number
                """, conversation);
        assertEquals(2, rows.size());
        assertEquals("USER", rows.get(0).get("sender"));
        assertEquals("My grandson visited me today.", rows.get(0).get("content"));
        assertEquals(patientAccount, rows.get(0).get("sender_uuid").toString());
        assertNull(rows.get(0).get("generated_by"));
        assertNotNull(rows.get(0).get("created_at"));
        assertEquals("ASSISTANT", rows.get(1).get("sender"));
        assertEquals(PlaceholderLlmClient.GENERATED_BY, rows.get(1).get("generated_by"));
        assertNull(rows.get(1).get("sender_uuid"));
    }
}
