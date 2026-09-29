package com.sih.nivara.assistant.speech;

/**
 * A speech provider call failed. The message is for logs and never contains audio, a transcript,
 * reply text, patient information or the API key; users only ever see a fixed, friendly message.
 */
public class SpeechException extends Exception {

    public enum Reason {
        /** Voice is switched off (provider none). */
        NOT_CONFIGURED,
        /** The provider cannot speak this language. */
        UNSUPPORTED_LANGUAGE,
        /** The call took too long. Retried once. */
        TIMEOUT,
        /** HTTP 429. Retried once. */
        RATE_LIMITED,
        /** HTTP 5xx or 408. Retried once. */
        PROVIDER_ERROR,
        /** Any other HTTP 4xx: bad key, no credits, a request the provider refused. Not retried. */
        REJECTED,
        /** The response could not be understood. Not retried. */
        INVALID_RESPONSE,
        /** The connection failed or the call was interrupted. Not retried. */
        UNAVAILABLE
    }

    private final Reason reason;

    public SpeechException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public boolean isRetryable() {
        return reason == Reason.TIMEOUT || reason == Reason.RATE_LIMITED || reason == Reason.PROVIDER_ERROR;
    }
}
