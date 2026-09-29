package com.sih.nivara.assistant.speech;

/**
 * Voice switched off, which is the default (provider none): every call fails with NOT_CONFIGURED,
 * without any network access, so the application runs without a speech key.
 */
public class DisabledSpeechClient implements SpeechClient {

    @Override
    public Transcript transcribe(AudioClip audio, String languageCode) throws SpeechException {
        throw new SpeechException(SpeechException.Reason.NOT_CONFIGURED, "Speech is not configured");
    }

    @Override
    public SpeechAudio synthesize(String text, String languageCode) throws SpeechException {
        throw new SpeechException(SpeechException.Reason.NOT_CONFIGURED, "Speech is not configured");
    }
}
