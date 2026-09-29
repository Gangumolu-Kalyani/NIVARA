package com.sih.nivara.assistant.speech;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration of the assistant's voice, under {@code nivara.assistant.speech}.
 *
 * <p>The default provider is NONE: voice is off and the application starts without any key. With
 * SARVAM, SARVAM_API_KEY must be set or the application refuses to start. The key is never logged:
 * {@link #toString} masks it.
 *
 * @param requestTimeout   the most one HTTP attempt may take
 * @param operationTimeout the most one transcription or one reply's speech may take, retries and
 *                         all of a long reply's parts included
 * @param maxAudioBytes    the largest recording accepted (2 MB)
 * @param minAudioBytes    anything smaller is treated as an empty recording
 */
@ConfigurationProperties(prefix = "nivara.assistant.speech")
public record SpeechProperties(
        Provider provider,
        String baseUrl,
        String apiKey,
        String sttModel,
        String ttsModel,
        String ttsSpeaker,
        Double ttsPace,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration operationTimeout,
        Duration retryBackoff,
        Integer maxAudioBytes,
        Integer minAudioBytes) {

    public enum Provider {
        NONE,
        SARVAM
    }

    public SpeechProperties {
        provider = provider != null ? provider : Provider.NONE;
        baseUrl = blankToNull(baseUrl) != null ? baseUrl.strip() : "https://api.sarvam.ai";
        apiKey = blankToNull(apiKey) != null ? apiKey.strip() : null;
        sttModel = blankToNull(sttModel) != null ? sttModel.strip() : "saaras:v4";
        ttsModel = blankToNull(ttsModel) != null ? ttsModel.strip() : "bulbul:v3";
        ttsSpeaker = blankToNull(ttsSpeaker) != null ? ttsSpeaker.strip() : "shubh";
        ttsPace = ttsPace != null ? ttsPace : 0.9;
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(5);
        requestTimeout = requestTimeout != null ? requestTimeout : Duration.ofSeconds(20);
        operationTimeout = operationTimeout != null ? operationTimeout : Duration.ofSeconds(45);
        retryBackoff = retryBackoff != null ? retryBackoff : Duration.ofSeconds(1);
        maxAudioBytes = maxAudioBytes != null ? maxAudioBytes : 2 * 1024 * 1024;
        minAudioBytes = minAudioBytes != null ? minAudioBytes : 1024;

        for (Duration duration : new Duration[] {connectTimeout, requestTimeout, operationTimeout}) {
            if (duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("nivara.assistant.speech: timeouts must be positive");
            }
        }
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("nivara.assistant.speech.retry-backoff must not be negative");
        }
        if (minAudioBytes < 1 || maxAudioBytes <= minAudioBytes) {
            throw new IllegalArgumentException("nivara.assistant.speech: max-audio-bytes must exceed min-audio-bytes");
        }
        // Bulbul v3 accepts 0.5 to 2.0
        if (ttsPace < 0.5 || ttsPace > 2.0) {
            throw new IllegalArgumentException("nivara.assistant.speech.tts-pace must be between 0.5 and 2.0");
        }
        if (provider == Provider.SARVAM) {
            if (apiKey == null) {
                throw new IllegalArgumentException("SARVAM_API_KEY is not set. The assistant's speech provider is "
                        + "sarvam, which needs an API key; set it, or set SPEECH_PROVIDER=none");
            }
            requireSafeBaseUrl(baseUrl);
        }
    }

    /** The key travels only to its provider: HTTPS, or plain HTTP to localhost for tests. */
    private static void requireSafeBaseUrl(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("nivara.assistant.speech.base-url is not a valid URL");
        }
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && local)) {
            throw new IllegalArgumentException("nivara.assistant.speech.base-url must use https");
        }
    }

    /** Treats blank values, and unresolved ${...} placeholders, as unset. */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() || value.strip().startsWith("${") ? null : value;
    }

    @Override
    public String toString() {
        return "SpeechProperties[provider=" + provider + ", baseUrl=" + baseUrl
                + ", apiKey=" + (apiKey == null ? "unset" : "****") + ", sttModel=" + sttModel
                + ", ttsModel=" + ttsModel + ", ttsSpeaker=" + ttsSpeaker + ", ttsPace=" + ttsPace
                + ", connectTimeout=" + connectTimeout + ", requestTimeout=" + requestTimeout
                + ", operationTimeout=" + operationTimeout + ", retryBackoff=" + retryBackoff
                + ", maxAudioBytes=" + maxAudioBytes + ", minAudioBytes=" + minAudioBytes + "]";
    }
}
