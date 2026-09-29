package com.sih.nivara.assistant.speech;

import com.sih.nivara.support.StubOpenRouterServer;
import com.sih.nivara.support.StubOpenRouterServer.Scripted;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Sarvam client against a local stub standing in for api.sarvam.ai: request format,
 * authentication header, language codes, parsing, splitting long text, retries and timeouts.
 * Never contacts Sarvam.
 */
class SarvamSpeechClientTest {

    private static final String KEY = "sarvam-test-key-123";
    /** Fake Ogg audio: the right first bytes, then ASCII, so it can be found in the recorded body. */
    private static final byte[] AUDIO = ("OggS" + "fake-opus-audio-".repeat(80)).getBytes(StandardCharsets.US_ASCII);

    private final StubOpenRouterServer stub = new StubOpenRouterServer();
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void stop() {
        stub.close();
    }

    private SarvamSpeechClient client(Duration requestTimeout, Duration operationTimeout) {
        return new SarvamSpeechClient(new SpeechProperties(SpeechProperties.Provider.SARVAM, stub.baseUrl(), KEY,
                null, null, "Ritu", null, Duration.ofSeconds(2), requestTimeout, operationTimeout,
                Duration.ofMillis(50), null, null), json);
    }

    private SarvamSpeechClient client() {
        return client(Duration.ofSeconds(5), Duration.ofSeconds(10));
    }

    private static String transcriptResponse(String transcript, String language) {
        return "{\"request_id\":\"r1\",\"transcript\":" + StubOpenRouterServer.quote(transcript)
                + ",\"language_code\":" + (language == null ? "null" : "\"" + language + "\"") + "}";
    }

    private static String audioResponse(byte[]... parts) {
        StringBuilder audios = new StringBuilder();
        for (byte[] part : parts) {
            if (!audios.isEmpty()) {
                audios.append(',');
            }
            audios.append('"').append(Base64.getEncoder().encodeToString(part)).append('"');
        }
        return "{\"request_id\":\"r2\",\"audios\":[" + audios + "]}";
    }

    // ---- speech to text ----------------------------------------------------------------------

    @Test
    void transcribesWithTheDocumentedMultipartRequest() throws Exception {
        stub.enqueue(Scripted.ok(transcriptResponse("मुझे दवा लेनी है", "hi-IN")));
        Transcript transcript = client().transcribe(new AudioClip(AUDIO, "audio/ogg"), "hi");

        assertEquals("मुझे दवा लेनी है", transcript.text());
        assertEquals("hi-IN", transcript.detectedLanguageCode());

        StubOpenRouterServer.Received received = stub.received().get(0);
        assertEquals("POST", received.method());
        assertTrue(received.path().endsWith("/speech-to-text"), received.path());
        assertEquals(KEY, received.header("api-subscription-key"));
        assertNull(received.header("Authorization"), "Sarvam uses its own header only");
        assertTrue(received.header("Content-Type").startsWith("multipart/form-data; boundary="));
        String body = received.body();
        assertFalse(body.contains(KEY), "the key is never in the body");
        assertTrue(body.contains("name=\"model\"\r\n\r\nsaaras:v4\r\n"), "saaras:v4 by default");
        assertTrue(body.contains("name=\"language_code\"\r\n\r\nhi-IN\r\n"));
        assertTrue(body.contains("name=\"file\"; filename=\"recording.ogg\"\r\nContent-Type: audio/ogg\r\n\r\n"));
        assertTrue(body.contains(new String(AUDIO, StandardCharsets.US_ASCII)), "the recording is sent as is");
        assertFalse(body.contains("name=\"mode\""), "transcribe is the default mode");
    }

    @Test
    void mapsConversationLanguagesToSarvamCodes() throws Exception {
        String[][] cases = {{"en", "en-IN"}, {"en-US", "en-IN"}, {"ta-IN", "ta-IN"}, {"or", "od-IN"},
                {"kok", "kok-IN"}, {"fr", "unknown"}, {"", "unknown"}};
        for (String[] c : cases) {
            stub.reset();
            stub.enqueue(Scripted.ok(transcriptResponse("hello", null)));
            client().transcribe(new AudioClip(AUDIO, "audio/ogg"), c[0]);
            assertTrue(stub.received().get(0).body().contains("name=\"language_code\"\r\n\r\n" + c[1] + "\r\n"),
                    c[0] + " -> " + c[1]);
        }
    }

    @Test
    void anEmptyTranscriptMeansNothingWasHeard() throws Exception {
        stub.enqueue(Scripted.ok(transcriptResponse("   ", "en-IN")));
        assertTrue(client().transcribe(new AudioClip(AUDIO, "audio/ogg"), "en").isEmpty());
    }

    // ---- text to speech ----------------------------------------------------------------------

    @Test
    void speaksWithTheDocumentedJsonRequest() throws Exception {
        byte[] mp3 = "ID3-fake-mp3-audio".getBytes(StandardCharsets.US_ASCII);
        stub.enqueue(Scripted.ok(audioResponse(mp3)));
        SpeechAudio audio = client().synthesize("Your next reminder is at 8 PM.", "en");

        assertEquals("audio/mpeg", audio.mediaType());
        assertArrayEquals(mp3, audio.bytes());

        StubOpenRouterServer.Received received = stub.received().get(0);
        assertTrue(received.path().endsWith("/text-to-speech"), received.path());
        assertEquals(KEY, received.header("api-subscription-key"));
        assertTrue(received.header("Content-Type").startsWith("application/json"));
        JsonNode body = json.readTree(received.body());
        assertEquals("Your next reminder is at 8 PM.", body.get("text").asString());
        assertEquals("en-IN", body.get("language_code").asString());
        assertEquals("bulbul:v3", body.get("model").asString());
        assertEquals("ritu", body.get("speaker").asString(), "speaker names must be lowercase");
        assertEquals(0.9, body.get("pace").asDouble());
        assertEquals("mp3", body.get("output_audio_codec").asString());
        assertFalse(received.body().contains(KEY));
    }

    @Test
    void longRepliesAreSpokenInPartsWithinTheLimit() throws Exception {
        String sentence = "This is one sentence of the reply. ";
        String reply = sentence.repeat(100).strip();          // about 3,500 characters
        byte[] first = "part-one".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "part-two".getBytes(StandardCharsets.US_ASCII);
        stub.enqueue(Scripted.ok(audioResponse(first)), Scripted.ok(audioResponse(second)));

        SpeechAudio audio = client().synthesize(reply, "en");
        assertEquals(2, stub.received().size());
        for (StubOpenRouterServer.Received received : stub.received()) {
            String text = json.readTree(received.body()).get("text").asString();
            assertTrue(text.length() <= SarvamSpeechClient.MAX_TTS_CHARS, "at most 2,500 characters per request");
            assertTrue(text.endsWith("."), "split at a sentence end");
        }
        assertEquals("part-onepart-two", new String(audio.bytes(), StandardCharsets.US_ASCII), "joined in order");
    }

    @Test
    void languagesBulbulCannotSpeakAreRefusedWithoutACall() {
        SpeechException e = assertThrows(SpeechException.class, () -> client().synthesize("Hello", "ur-IN"));
        assertEquals(SpeechException.Reason.UNSUPPORTED_LANGUAGE, e.reason());
        assertEquals(0, stub.received().size());
    }

    // ---- failures ----------------------------------------------------------------------------

    @Test
    void retriesOnceOnRateLimitServerErrorsAndTimeouts() throws Exception {
        stub.enqueue(Scripted.status(429).withRetryAfter("0"), Scripted.ok(transcriptResponse("hello", "en-IN")));
        assertEquals("hello", client().transcribe(new AudioClip(AUDIO, "audio/ogg"), "en").text());
        assertEquals(2, stub.received().size());

        stub.reset();
        stub.enqueue(Scripted.status(503), Scripted.status(500));
        SpeechException e = assertThrows(SpeechException.class, () -> client().synthesize("Hello", "en"));
        assertEquals(SpeechException.Reason.PROVIDER_ERROR, e.reason());
        assertEquals(2, stub.received().size(), "at most one retry");

        stub.reset();
        stub.enqueue(Scripted.ok(transcriptResponse("slow", "en-IN")).delayed(1500),
                Scripted.ok(transcriptResponse("hello", "en-IN")));
        assertEquals("hello", client(Duration.ofMillis(400), Duration.ofSeconds(5))
                .transcribe(new AudioClip(AUDIO, "audio/ogg"), "en").text());
        assertEquals(2, stub.received().size());

        stub.reset();
        stub.enqueue(Scripted.ok(transcriptResponse("slow", "en-IN")).delayed(1500),
                Scripted.ok(transcriptResponse("slow", "en-IN")).delayed(1500));
        assertEquals(SpeechException.Reason.TIMEOUT, assertThrows(SpeechException.class,
                () -> client(Duration.ofMillis(400), Duration.ofSeconds(5))
                        .transcribe(new AudioClip(AUDIO, "audio/ogg"), "en")).reason());
        assertEquals(2, stub.received().size());
    }

    @Test
    void refusedRequestsAndUnreadableResponsesAreNotRetried() {
        for (int status : new int[] {400, 403, 422}) {
            stub.reset();
            stub.enqueue(Scripted.status(status), Scripted.ok(transcriptResponse("never", null)));
            SpeechException e = assertThrows(SpeechException.class,
                    () -> client().transcribe(new AudioClip(AUDIO, "audio/ogg"), "en"));
            assertEquals(SpeechException.Reason.REJECTED, e.reason());
            assertEquals(1, stub.received().size(), "HTTP " + status + " is not retried");
            assertFalse(e.getMessage().contains(KEY));
        }
        for (String body : new String[] {"not json", "{}", "{\"audios\":[]}", "{\"audios\":[\"%%%\"]}"}) {
            stub.reset();
            stub.enqueue(Scripted.ok(body));
            SpeechException e = assertThrows(SpeechException.class, () -> client().synthesize("Hello", "en"), body);
            assertEquals(SpeechException.Reason.INVALID_RESPONSE, e.reason(), body);
            assertEquals(1, stub.received().size());
        }
    }

    // ---- configuration -----------------------------------------------------------------------

    @Test
    void configurationDefaultsAndTheKey() {
        SpeechProperties none = new SpeechProperties(null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        assertEquals(SpeechProperties.Provider.NONE, none.provider(), "voice is off by default");
        assertEquals("https://api.sarvam.ai", none.baseUrl());
        assertEquals("saaras:v4", none.sttModel());
        assertEquals("bulbul:v3", none.ttsModel());
        assertEquals(2 * 1024 * 1024, none.maxAudioBytes());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> new SpeechProperties(
                SpeechProperties.Provider.SARVAM, null, "${SARVAM_API_KEY}", null, null, null, null,
                null, null, null, null, null, null));
        assertTrue(missing.getMessage().contains("SARVAM_API_KEY"));
        assertThrows(IllegalArgumentException.class, () -> new SpeechProperties(SpeechProperties.Provider.SARVAM,
                "http://api.example.com", KEY, null, null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SpeechProperties(SpeechProperties.Provider.NONE,
                null, null, null, null, null, 3.0, null, null, null, null, null, null));

        SpeechProperties sarvam = new SpeechProperties(SpeechProperties.Provider.SARVAM, null, "  " + KEY + "  ",
                null, null, null, null, null, null, null, null, null, null);
        assertEquals(KEY, sarvam.apiKey(), "the key is trimmed");
        assertFalse(sarvam.toString().contains(KEY));
        assertTrue(sarvam.toString().contains("apiKey=****"));
    }
}
