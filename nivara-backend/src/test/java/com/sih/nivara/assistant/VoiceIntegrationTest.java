package com.sih.nivara.assistant;

import com.sih.nivara.assistant.llm.PlaceholderLlmClient;
import com.sih.nivara.assistant.speech.AudioClip;
import com.sih.nivara.assistant.speech.SpeechClient;
import com.sih.nivara.assistant.speech.SpeechAudio;
import com.sih.nivara.assistant.speech.SpeechException;
import com.sih.nivara.assistant.speech.Transcript;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Voice end to end over HTTP, with fake speech providers that record every call: who may
 * transcribe and hear what, that nothing unauthorized or unusable reaches a provider, that
 * transcription creates no message, and that failures are friendly.
 */
@ExtendWith(OutputCaptureExtension.class)
class VoiceIntegrationTest extends EmbeddedPostgresIntegrationTest {

    private static final String CONVERSATIONS = "/api/assistant/conversations";
    private static final String HEARD = "My grandson visited me today";
    private static final byte[] SPOKEN = "ID3-fake-mp3".getBytes(StandardCharsets.US_ASCII);

    /** Stands in for Sarvam in both directions, recording each call and failing on demand. */
    static final class FakeSpeech implements SpeechClient {
        record Call(String what, String languageCode, String mediaType, String text) {
        }

        final List<Call> calls = new CopyOnWriteArrayList<>();
        volatile SpeechException failWith;
        volatile String transcript = HEARD;

        @Override
        public Transcript transcribe(AudioClip audio, String languageCode) throws SpeechException {
            calls.add(new Call("stt", languageCode, audio.mediaType(), null));
            if (failWith != null) {
                throw failWith;
            }
            return new Transcript(transcript, languageCode);
        }

        @Override
        public SpeechAudio synthesize(String text, String languageCode) throws SpeechException {
            calls.add(new Call("tts", languageCode, null, text));
            if (failWith != null) {
                throw failWith;
            }
            return new SpeechAudio(SPOKEN, "audio/mpeg");
        }
    }

    @TestConfiguration
    static class FakeSpeechConfiguration {
        static final FakeSpeech SPEECH = new FakeSpeech();

        @Bean
        @Primary
        SpeechClient fakeSpeechClient() {
            return SPEECH;
        }
    }

    @Autowired Environment environment;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper jsonMapper;

    private final FakeSpeech speech = FakeSpeechConfiguration.SPEECH;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void resetSpeech() {
        speech.calls.clear();
        speech.failWith = null;
        speech.transcript = HEARD;
    }

    // ---- helpers -----------------------------------------------------------------------------

    /** A small but valid-looking WebM recording: the EBML header, then padding. */
    private static byte[] webm(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = 0x1A;
        bytes[1] = 0x45;
        bytes[2] = (byte) 0xDF;
        bytes[3] = (byte) 0xA3;
        return bytes;
    }

    private record Response(int status, String contentType, String cacheControl, byte[] body) {
        JsonNode json(JsonMapper mapper) {
            return mapper.readTree(new String(body, StandardCharsets.UTF_8));
        }
    }

    /** POSTs a multipart request; partName null sends a form without the audio part. */
    private Response upload(String token, String conversation, String partName, String contentType, byte[] audio) {
        String boundary = "test-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        String name = partName != null ? partName : "other";
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name
                + "\"; filename=\"recording\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(audio);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(CONVERSATIONS + "/" + conversation + "/transcriptions"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        return send(token, request);
    }

    private Response speechOf(String token, String conversation, String message) {
        return send(token, HttpRequest.newBuilder(uri(CONVERSATIONS + "/" + conversation + "/messages/" + message + "/speech")).GET());
    }

    private Response send(String token, HttpRequest.Builder request) {
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<byte[]> response = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.headers().firstValue("Content-Type").orElse(null),
                    response.headers().firstValue("Cache-Control").orElse(null), response.body());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + environment.getProperty("local.server.port") + path);
    }

    private int messageCount(String conversation) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM assistant_messages m JOIN assistant_conversations c ON c.id = m.conversation_id
                WHERE c.uuid = ?::uuid""", Integer.class, conversation);
    }

    private String newConversation(String token, String body) {
        return text(expect(201, "POST", CONVERSATIONS, token, body).body(), "uuid");
    }

    // ---- the happy path ----------------------------------------------------------------------

    @Test
    void aPatientSpeaksTheTranscriptIsSentAsAnOrdinaryMessageAndTheReplyIsSpoken(CapturedOutput output) {
        String caregiver = caregiverToken("Ravi Kumar");
        String patient = createPatient(caregiver, "Lakshmi Rao");
        String patientToken = patientToken(caregiver, patient);
        String conversation = newConversation(patientToken, null);

        Response transcribed = upload(patientToken, conversation, "audio", "audio/webm;codecs=opus", webm(4000));
        assertEquals(200, transcribed.status(), new String(transcribed.body(), StandardCharsets.UTF_8));
        JsonNode result = transcribed.json(jsonMapper);
        assertEquals(HEARD, text(result, "transcript"));
        assertEquals("en", text(result, "languageCode"), "the conversation's language");
        assertEquals(0, messageCount(conversation), "transcribing creates no message");
        assertEquals(List.of(new FakeSpeech.Call("stt", "en", "audio/webm", null)), speech.calls);

        // The transcript goes through the existing, unchanged message endpoint
        JsonNode exchange = expect(200, "POST", CONVERSATIONS + "/" + conversation + "/messages", patientToken,
                "{\"content\":\"" + text(result, "transcript") + "\"}").body();
        assertEquals(2, messageCount(conversation), "exactly the usual two messages");
        String reply = text(exchange.get("reply"), "uuid");

        Response spoken = speechOf(patientToken, conversation, reply);
        assertEquals(200, spoken.status());
        assertEquals("audio/mpeg", spoken.contentType());
        assertTrue(spoken.cacheControl().contains("no-store"));
        assertArrayEquals(SPOKEN, spoken.body());
        assertEquals(new FakeSpeech.Call("tts", "en", null, PlaceholderLlmClient.REPLY), speech.calls.get(1));
        assertEquals(2, messageCount(conversation), "speaking creates no message either");

        // Nothing heard or said is logged
        assertFalse(output.getAll().contains(HEARD), "no transcript in the logs");
        assertFalse(output.getAll().contains(PlaceholderLlmClient.REPLY), "no reply text in the logs");
    }

    @Test
    void theConversationsLanguageReachesBothProvidersAndSafarisFormatIsAccepted() {
        String caregiver = caregiverToken("Asha Menon");
        String patient = createPatient(caregiver, "Hindi Patient");
        String conversation = newConversation(caregiver, "{\"patientUuid\":\"" + patient + "\",\"languageCode\":\"hi-IN\"}");

        byte[] mp4 = new byte[4000];
        byte[] header = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        System.arraycopy(header, 0, mp4, 0, header.length);
        Response transcribed = upload(caregiver, conversation, "audio", "audio/mp4", mp4);
        assertEquals(200, transcribed.status());
        assertEquals("hi-IN", text(transcribed.json(jsonMapper), "languageCode"));
        assertEquals(new FakeSpeech.Call("stt", "hi-IN", "audio/mp4", null), speech.calls.get(0));

        String reply = text(expect(200, "POST", CONVERSATIONS + "/" + conversation + "/messages", caregiver,
                "{\"content\":\"Namaste\"}").body().get("reply"), "uuid");
        assertEquals(200, speechOf(caregiver, conversation, reply).status());
        assertEquals("hi-IN", speech.calls.get(1).languageCode());
    }

    // ---- recordings that never reach the provider --------------------------------------------

    @Test
    void unusableRecordingsAreRefusedBeforeTheProvider() {
        String caregiver = caregiverToken("Meera Iyer");
        String conversation = newConversation(caregiver, null);

        assertEquals(400, upload(caregiver, conversation, null, "audio/webm", webm(4000)).status(), "no audio part");
        assertEquals(400, upload(caregiver, conversation, "audio", "audio/webm", new byte[0]).status(), "empty");
        assertEquals(400, upload(caregiver, conversation, "audio", "audio/webm", webm(500)).status(), "too short");
        assertEquals(413, upload(caregiver, conversation, "audio", "audio/webm", webm(2 * 1024 * 1024 + 1)).status());
        assertEquals(415, upload(caregiver, conversation, "audio", "text/plain", webm(4000)).status(), "not audio");
        assertEquals(415, upload(caregiver, conversation, "audio", "audio/webm",
                "<html>not audio at all</html>".repeat(100).getBytes(StandardCharsets.UTF_8)).status(), "not really WebM");
        assertEquals(415, call("POST", CONVERSATIONS + "/" + conversation + "/transcriptions", caregiver,
                "{\"audio\":\"x\"}").status(), "not multipart");

        expect(200, "POST", CONVERSATIONS + "/" + conversation + "/close", caregiver, null);
        assertEquals(409, upload(caregiver, conversation, "audio", "audio/webm", webm(4000)).status(), "closed");

        assertTrue(speech.calls.isEmpty(), "the provider was never called");
        assertEquals(0, messageCount(conversation));
    }

    // ---- authorization -----------------------------------------------------------------------

    @Test
    void nobodyCanUseAnotherAccountsConversationAndNoAudioLeavesForIt() {
        String caregiver = caregiverToken("Kiran Rao");
        String patientA = createPatient(caregiver, "Patient A");
        String patientB = createPatient(caregiver, "Patient B");
        String tokenA = patientToken(caregiver, patientA);
        String tokenB = patientToken(caregiver, patientB);
        String conversationA = newConversation(tokenA, null);
        String reply = text(expect(200, "POST", CONVERSATIONS + "/" + conversationA + "/messages", tokenA,
                "{\"content\":\"Hello\"}").body().get("reply"), "uuid");
        speech.calls.clear();

        // Patient B, and the caregiver (who is OWNER of patient A but not of this conversation)
        for (String other : new String[] {tokenB, caregiver}) {
            assertEquals(404, upload(other, conversationA, "audio", "audio/webm", webm(4000)).status());
            assertEquals(404, speechOf(other, conversationA, reply).status());
        }
        // Without a valid token
        assertEquals(401, upload(null, conversationA, "audio", "audio/webm", webm(4000)).status());
        assertEquals(401, speechOf("not-a-token", conversationA, reply).status());
        assertEquals(404, upload(tokenA, UUID.randomUUID().toString(), "audio", "audio/webm", webm(4000)).status());

        assertTrue(speech.calls.isEmpty(), "no audio or text reached the provider for anyone else");
        assertEquals(2, messageCount(conversationA));
    }

    @Test
    void aCaregiverWhoLosesAccessLosesVoiceAtOnce() {
        String owner = caregiverToken("Owner Carer");
        String patient = createPatient(owner, "Shared Patient");
        String viewer = caregiverToken("Viewer Carer");
        String viewerUuid = text(expect(200, "GET", "/api/auth/me", viewer, null).body(), "uuid");
        expect(201, "POST", "/api/patients/" + patient + "/caregivers", owner,
                "{\"caregiverUserUuid\":\"" + viewerUuid + "\",\"relationship\":\"SON\",\"accessLevel\":\"VIEWER\"}");
        String conversation = newConversation(viewer, "{\"patientUuid\":\"" + patient + "\"}");
        String reply = text(expect(200, "POST", CONVERSATIONS + "/" + conversation + "/messages", viewer,
                "{\"content\":\"How is she?\"}").body().get("reply"), "uuid");
        assertEquals(200, upload(viewer, conversation, "audio", "audio/webm", webm(4000)).status());
        speech.calls.clear();

        expect(204, "DELETE", "/api/patients/" + patient + "/caregivers/" + viewerUuid, owner, null);
        assertEquals(404, upload(viewer, conversation, "audio", "audio/webm", webm(4000)).status());
        assertEquals(404, speechOf(viewer, conversation, reply).status());
        assertTrue(speech.calls.isEmpty());
    }

    @Test
    void onlyAssistantRepliesOfThisConversationCanBeSpoken() {
        String caregiver = caregiverToken("Farah Khan");
        String first = newConversation(caregiver, null);
        String second = newConversation(caregiver, null);
        JsonNode exchange = expect(200, "POST", CONVERSATIONS + "/" + first + "/messages", caregiver,
                "{\"content\":\"What alerts are there?\"}").body();
        speech.calls.clear();

        assertEquals(404, speechOf(caregiver, first, text(exchange.get("userMessage"), "uuid")).status(), "a user message");
        assertEquals(404, speechOf(caregiver, second, text(exchange.get("reply"), "uuid")).status(), "another conversation");
        assertEquals(404, speechOf(caregiver, first, UUID.randomUUID().toString()).status());
        assertEquals(400, speechOf(caregiver, first, "not-a-uuid").status());
        assertTrue(speech.calls.isEmpty());
        assertEquals(200, speechOf(caregiver, first, text(exchange.get("reply"), "uuid")).status());
    }

    // ---- provider outcomes -------------------------------------------------------------------

    @Test
    void failuresAreFriendlyAndStoreNothing(CapturedOutput output) {
        String caregiver = caregiverToken("Nisha Pillai");
        String conversation = newConversation(caregiver, null);
        String reply = text(expect(200, "POST", CONVERSATIONS + "/" + conversation + "/messages", caregiver,
                "{\"content\":\"Hello\"}").body().get("reply"), "uuid");

        speech.transcript = "   ";
        Response nothingHeard = upload(caregiver, conversation, "audio", "audio/webm", webm(4000));
        assertEquals(422, nothingHeard.status());
        assertTrue(new String(nothingHeard.body(), StandardCharsets.UTF_8).contains("didn't catch that"));

        speech.failWith = new SpeechException(SpeechException.Reason.PROVIDER_ERROR, "Speech provider failed (HTTP 503) secret-internal-detail");
        Response failed = upload(caregiver, conversation, "audio", "audio/webm", webm(4000));
        assertEquals(503, failed.status());
        String failedBody = new String(failed.body(), StandardCharsets.UTF_8);
        assertTrue(failedBody.contains("Voice is not available right now"), failedBody);
        assertFalse(failedBody.contains("secret-internal-detail"));
        assertEquals(503, speechOf(caregiver, conversation, reply).status());

        speech.failWith = new SpeechException(SpeechException.Reason.UNSUPPORTED_LANGUAGE, "no voice");
        assertEquals(422, speechOf(caregiver, conversation, reply).status());

        assertEquals(2, messageCount(conversation), "no failure added a message");
        assertFalse(output.getAll().contains("secret-internal-detail"), "only the reason is logged");
    }
}
