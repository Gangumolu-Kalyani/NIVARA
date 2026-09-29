package com.sih.nivara.assistant.llm;

/**
 * A model call failed. The message is for logs and never contains the prompt, the reply, patient
 * information or the API key; the user only ever sees the fixed fallback reply.
 */
public class LlmException extends Exception {

    public enum Reason {
        /** The call took longer than allowed. Retried once. */
        TIMEOUT,
        /** The provider is rate limiting (HTTP 429). Retried once. */
        RATE_LIMITED,
        /** The provider failed (HTTP 5xx, 408, or an error inside a 200 response). Retried once. */
        PROVIDER_ERROR,
        /** The request was refused (other HTTP 4xx: bad key, no credits, bad request). Not retried. */
        REJECTED,
        /** The response could not be understood. Not retried. */
        INVALID_RESPONSE,
        /** The connection failed, or the call was interrupted. Not retried. */
        UNAVAILABLE
    }

    private final Reason reason;

    public LlmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public LlmException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    /** Whether one more attempt may help: timeouts, rate limits and provider failures. */
    public boolean isRetryable() {
        return reason == Reason.TIMEOUT || reason == Reason.RATE_LIMITED || reason == Reason.PROVIDER_ERROR;
    }
}
