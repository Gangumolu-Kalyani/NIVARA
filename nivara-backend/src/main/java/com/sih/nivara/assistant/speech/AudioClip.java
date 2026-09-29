package com.sih.nivara.assistant.speech;

/**
 * A recording to transcribe, held in memory only for the request. mediaType is the validated
 * base type, such as {@code audio/webm}, without codec parameters.
 */
public record AudioClip(byte[] bytes, String mediaType) {

    /** Never prints the audio. */
    @Override
    public String toString() {
        return "AudioClip[" + mediaType + ", " + bytes.length + " bytes]";
    }
}
