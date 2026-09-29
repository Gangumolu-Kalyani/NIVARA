package com.sih.nivara.assistant.speech;

/** Synthesized speech, held in memory only for the response. mediaType is its Content-Type. */
public record SpeechAudio(byte[] bytes, String mediaType) {

    /** Never prints the audio. */
    @Override
    public String toString() {
        return "SpeechAudio[" + mediaType + ", " + bytes.length + " bytes]";
    }
}
