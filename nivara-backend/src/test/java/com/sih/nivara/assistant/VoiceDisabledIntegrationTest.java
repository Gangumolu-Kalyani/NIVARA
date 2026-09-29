package com.sih.nivara.assistant;

import com.sih.nivara.assistant.speech.DisabledSpeechClient;
import com.sih.nivara.assistant.speech.SpeechToText;
import com.sih.nivara.assistant.speech.TextToSpeech;
import com.sih.nivara.support.EmbeddedPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the default configuration, provider none: the application runs without any speech key,
 * the text assistant works, and the voice endpoints answer 503 without calling anybody.
 */
class VoiceDisabledIntegrationTest extends EmbeddedPostgresIntegrationTest {

    @Autowired SpeechToText speechToText;
    @Autowired TextToSpeech textToSpeech;
    @Autowired Environment environment;

    @Test
    void voiceIsOffByDefaultAndSaysSo() throws Exception {
        assertInstanceOf(DisabledSpeechClient.class, speechToText);
        assertInstanceOf(DisabledSpeechClient.class, textToSpeech);

        String caregiver = caregiverToken("No Voice Carer");
        String conversation = text(expect(201, "POST", "/api/assistant/conversations", caregiver, null).body(), "uuid");
        String reply = text(expect(200, "POST", "/api/assistant/conversations/" + conversation + "/messages",
                caregiver, "{\"content\":\"Hello\"}").body().get("reply"), "uuid");

        String boundary = "b";
        byte[] audio = new byte[2000];
        audio[0] = 0x1A;
        audio[1] = 0x45;
        audio[2] = (byte) 0xDF;
        audio[3] = (byte) 0xA3;
        byte[] head = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"r\"\r\n"
                + "Content-Type: audio/webm\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[head.length + audio.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(audio, 0, body, head.length, audio.length);
        System.arraycopy(tail, 0, body, head.length + audio.length, tail.length);

        String base = "http://localhost:" + environment.getProperty("local.server.port") + "/api/assistant/conversations/" + conversation;
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> transcribed = http.send(HttpRequest.newBuilder(URI.create(base + "/transcriptions"))
                .header("Authorization", "Bearer " + caregiver)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, transcribed.statusCode());
        assertTrue(transcribed.body().contains("Voice is not set up"), transcribed.body());

        HttpResponse<String> spoken = http.send(HttpRequest.newBuilder(URI.create(base + "/messages/" + reply + "/speech"))
                .header("Authorization", "Bearer " + caregiver).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, spoken.statusCode());
    }
}
