package com.sih.nivara.assistant.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sarvam AI's speech APIs, as documented at docs.sarvam.ai:
 *
 * <ul>
 *   <li>speech-to-text: {@code POST {baseUrl}/speech-to-text}, multipart/form-data with
 *       {@code file}, {@code model} (saaras:v4 by default) and {@code language_code};
 *       answers {@code {transcript, language_code}}. At most 30 seconds of audio.</li>
 *   <li>text-to-speech: {@code POST {baseUrl}/text-to-speech}, JSON with {@code text} (at most
 *       2,500 characters for bulbul:v3), {@code language_code}, {@code model}, {@code speaker},
 *       {@code pace} and {@code output_audio_codec}; answers {@code {request_id, audios: [base64]}}.</li>
 * </ul>
 *
 * <p>Authenticates with the {@code api-subscription-key} header only. Keeps nothing: audio and text
 * exist only for the call. Logs only failure reasons and HTTP status, never audio, transcripts,
 * text or the key. Each HTTP attempt may take requestTimeout; a timeout, HTTP 429, 408 or 5xx is
 * retried once if the operation's deadline allows. Everything else fails at once.
 */
public class SarvamSpeechClient implements SpeechClient {

    private static final Logger log = LoggerFactory.getLogger(SarvamSpeechClient.class);

    /** Bulbul v3's limit on text per request. */
    static final int MAX_TTS_CHARS = 2500;

    /** A reply is at most 4,000 characters, so two parts; anything beyond this many is not spoken. */
    static final int MAX_TTS_PARTS = 4;

    private static final Duration MINIMUM_ATTEMPT = Duration.ofSeconds(1);
    private static final Duration MAXIMUM_RETRY_AFTER = Duration.ofSeconds(5);

    private final SpeechProperties properties;
    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final URI speechToTextEndpoint;
    private final URI textToSpeechEndpoint;

    public SarvamSpeechClient(SpeechProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        String base = properties.baseUrl().replaceAll("/+$", "");
        this.speechToTextEndpoint = URI.create(base + "/speech-to-text");
        this.textToSpeechEndpoint = URI.create(base + "/text-to-speech");
    }

    // ---- speech to text ----------------------------------------------------------------------

    @Override
    public Transcript transcribe(AudioClip audio, String languageCode) throws SpeechException {
        String boundary = "nivara-" + UUID.randomUUID();
        byte[] body = multipartBody(boundary, audio, SarvamLanguages.forSpeechToText(languageCode));
        Instant deadline = Instant.now().plus(properties.operationTimeout());

        String response = send(deadline, () -> HttpRequest.newBuilder(speechToTextEndpoint)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));

        JsonNode root = readObject(response);
        JsonNode transcript = root.get("transcript");
        if (transcript == null || !transcript.isString()) {
            throw new SpeechException(SpeechException.Reason.INVALID_RESPONSE, "The transcription has no transcript");
        }
        return new Transcript(transcript.asString().strip(), text(root, "language_code"));
    }

    private byte[] multipartBody(String boundary, AudioClip audio, String sarvamLanguage) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(audio.bytes().length + 512);
        writeField(out, boundary, "model", properties.sttModel());
        writeField(out, boundary, "language_code", sarvamLanguage);
        write(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"recording."
                + AudioUploads.extensionOf(audio.mediaType()) + "\"\r\n"
                + "Content-Type: " + audio.mediaType() + "\r\n\r\n");
        out.writeBytes(audio.bytes());
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) {
        write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    // ---- text to speech ----------------------------------------------------------------------

    /**
     * Speaks the text as MP3. Text longer than one request allows is split at sentence ends and
     * spoken part by part; the MP3 parts are joined in order, which MP3 players handle as one
     * stream.
     */
    @Override
    public SpeechAudio synthesize(String text, String languageCode) throws SpeechException {
        String sarvamLanguage = SarvamLanguages.forTextToSpeech(languageCode)
                .orElseThrow(() -> new SpeechException(SpeechException.Reason.UNSUPPORTED_LANGUAGE,
                        "Text-to-speech does not support the conversation's language"));
        List<String> parts = SpeechTextChunks.split(text, MAX_TTS_CHARS);
        if (parts.isEmpty()) {
            throw new SpeechException(SpeechException.Reason.INVALID_RESPONSE, "There is no text to speak");
        }
        if (parts.size() > MAX_TTS_PARTS) {
            parts = parts.subList(0, MAX_TTS_PARTS);
        }

        Instant deadline = Instant.now().plus(properties.operationTimeout());
        ByteArrayOutputStream audio = new ByteArrayOutputStream();
        for (String part : parts) {
            String body = jsonMapper.writeValueAsString(textToSpeechBody(part, sarvamLanguage));
            String response = send(deadline, () -> HttpRequest.newBuilder(textToSpeechEndpoint)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)));
            JsonNode audios = readObject(response).get("audios");
            if (audios == null || !audios.isArray() || audios.isEmpty()) {
                throw new SpeechException(SpeechException.Reason.INVALID_RESPONSE, "The speech response has no audio");
            }
            for (JsonNode encoded : audios) {
                try {
                    audio.writeBytes(Base64.getDecoder().decode(encoded.asString()));
                } catch (IllegalArgumentException e) {
                    throw new SpeechException(SpeechException.Reason.INVALID_RESPONSE, "The speech audio is not base64");
                }
            }
        }
        return new SpeechAudio(audio.toByteArray(), "audio/mpeg");
    }

    ObjectNode textToSpeechBody(String text, String sarvamLanguage) {
        ObjectNode body = jsonMapper.createObjectNode();
        body.put("text", text);
        body.put("language_code", sarvamLanguage);
        body.put("model", properties.ttsModel());
        body.put("speaker", properties.ttsSpeaker().toLowerCase(java.util.Locale.ROOT));
        body.put("pace", properties.ttsPace());
        body.put("output_audio_codec", "mp3");
        return body;
    }

    // ---- transport ---------------------------------------------------------------------------

    @FunctionalInterface
    private interface RequestTemplate {
        HttpRequest.Builder build();
    }

    /** Sends, retrying once when that may help and the deadline allows; answers the 200 body. */
    private String send(Instant deadline, RequestTemplate template) throws SpeechException {
        try {
            return attempt(deadline, template);
        } catch (RetryableAttempt first) {
            Duration wait = first.retryAfter != null ? first.retryAfter : properties.retryBackoff();
            if (Duration.between(Instant.now(), deadline).compareTo(wait.plus(MINIMUM_ATTEMPT)) < 0) {
                throw first.cause;
            }
            log.info("Speech provider call failed ({}); retrying once", first.cause.reason());
            sleep(wait);
            try {
                return attempt(deadline, template);
            } catch (RetryableAttempt second) {
                throw second.cause;
            }
        }
    }

    private String attempt(Instant deadline, RequestTemplate template) throws SpeechException, RetryableAttempt {
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.compareTo(Duration.ZERO) <= 0) {
            throw new SpeechException(SpeechException.Reason.TIMEOUT, "No time left for the speech call");
        }
        Duration timeout = remaining.compareTo(properties.requestTimeout()) < 0 ? remaining : properties.requestTimeout();
        HttpRequest request = template.build()
                .timeout(timeout)
                .header("api-subscription-key", properties.apiKey())
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new RetryableAttempt(new SpeechException(SpeechException.Reason.TIMEOUT, "The speech call timed out"), null);
        } catch (IOException e) {
            throw new SpeechException(SpeechException.Reason.UNAVAILABLE, "Could not reach the speech provider");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechException(SpeechException.Reason.UNAVAILABLE, "The speech call was interrupted");
        }

        int status = response.statusCode();
        if (status == 429) {
            throw new RetryableAttempt(new SpeechException(SpeechException.Reason.RATE_LIMITED,
                    "Speech provider rate limit (HTTP 429)"), retryAfter(response));
        }
        if (status == 408 || status >= 500) {
            throw new RetryableAttempt(new SpeechException(SpeechException.Reason.PROVIDER_ERROR,
                    "Speech provider failed (HTTP " + status + ")"), retryAfter(response));
        }
        if (status != 200) {
            throw new SpeechException(SpeechException.Reason.REJECTED,
                    "Speech provider refused the request (HTTP " + status + ")");
        }
        return response.body();
    }

    private JsonNode readObject(String body) throws SpeechException {
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            throw new SpeechException(SpeechException.Reason.INVALID_RESPONSE, "The speech response is not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new SpeechException(SpeechException.Reason.INVALID_RESPONSE, "The speech response is not a JSON object");
        }
        return root;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
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

    private static void sleep(Duration wait) throws SpeechException {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpeechException(SpeechException.Reason.UNAVAILABLE, "The speech call was interrupted");
        }
    }

    /** An attempt that failed in a way one more attempt might fix. */
    private static final class RetryableAttempt extends Exception {
        private final SpeechException cause;
        private final Duration retryAfter;

        RetryableAttempt(SpeechException cause, Duration retryAfter) {
            super(cause.getMessage(), null, false, false);
            this.cause = cause;
            this.retryAfter = retryAfter;
        }
    }
}
