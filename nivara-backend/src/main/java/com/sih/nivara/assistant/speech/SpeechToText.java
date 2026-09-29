package com.sih.nivara.assistant.speech;

/**
 * Turns a short spoken recording into text. The seam between the assistant and a speech
 * recognition provider: implementations only talk to their provider, keep nothing, and make no
 * authorization decision. VoiceService has already checked the caller, the conversation and the
 * recording before calling one.
 */
public interface SpeechToText {

    /**
     * The words spoken in the recording, in the given language (a conversation's language_code;
     * the implementation maps it to its provider's codes). An empty transcript means no speech was
     * recognized.
     */
    Transcript transcribe(AudioClip audio, String languageCode) throws SpeechException;
}
