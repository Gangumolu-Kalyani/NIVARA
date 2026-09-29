package com.sih.nivara.assistant.speech;

/**
 * What was said. detectedLanguageCode is the provider's view of the spoken language, when it
 * reports one. The text is personal data: it is returned to the user and never logged.
 */
public record Transcript(String text, String detectedLanguageCode) {

    public boolean isEmpty() {
        return text == null || text.isBlank();
    }

    /** Never prints what was said. */
    @Override
    public String toString() {
        return "Transcript[" + (text == null ? 0 : text.length()) + " chars, " + detectedLanguageCode + "]";
    }
}
