package com.sih.nivara.assistant.speech;

/**
 * Turns an assistant reply into spoken audio. The seam between the assistant and a speech
 * synthesis provider: implementations only talk to their provider, keep nothing, and make no
 * authorization decision. VoiceService has already checked the caller and that the text is an
 * assistant message of their conversation.
 */
public interface TextToSpeech {

    /**
     * The text spoken in the given language (a conversation's language_code). Throws with reason
     * UNSUPPORTED_LANGUAGE when the provider cannot speak that language.
     */
    SpeechAudio synthesize(String text, String languageCode) throws SpeechException;
}
