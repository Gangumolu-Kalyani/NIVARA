package com.sih.nivara.assistant.dto.response;

/**
 * What was heard in a recording. Nothing has been stored: to say it to the assistant, send
 * transcript to the conversation's messages endpoint. languageCode is the conversation's language,
 * used for recognition; detectedLanguageCode is what the provider heard, when it reports one.
 */
public record TranscriptionResponse(
        String transcript,
        String languageCode,
        String detectedLanguageCode) {
}
