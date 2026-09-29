package com.sih.nivara.assistant.speech;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Language mapping, accepted recordings and text splitting, on their own. */
class SpeechRulesTest {

    @Test
    void languageCodesMapToSarvamsCodes() {
        assertEquals("hi-IN", SarvamLanguages.forSpeechToText("hi"));
        assertEquals("hi-IN", SarvamLanguages.forSpeechToText("hi-IN"));
        assertEquals("en-IN", SarvamLanguages.forSpeechToText("en-GB"));
        assertEquals("od-IN", SarvamLanguages.forSpeechToText("or-IN"));
        assertEquals("mni-IN", SarvamLanguages.forSpeechToText("mni"));
        assertEquals("unknown", SarvamLanguages.forSpeechToText("de"), "detected by Sarvam");
        assertEquals("unknown", SarvamLanguages.forSpeechToText(null));

        assertEquals(Optional.of("ta-IN"), SarvamLanguages.forTextToSpeech("ta"));
        assertEquals(Optional.of("od-IN"), SarvamLanguages.forTextToSpeech("or"));
        assertEquals(Optional.empty(), SarvamLanguages.forTextToSpeech("ur-IN"), "recognized but not spoken");
        assertEquals(Optional.empty(), SarvamLanguages.forTextToSpeech("de"));
    }

    @Test
    void onlyRealAudioInAnAcceptedFormatIsAccepted() {
        byte[] webm = bytes(0x1A, 0x45, 0xDF, 0xA3, 0x42, 0x86);
        byte[] ogg = "OggS....".getBytes(StandardCharsets.US_ASCII);
        byte[] mp4 = new byte[] {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        byte[] wav = "RIFF....WAVEfmt ".getBytes(StandardCharsets.US_ASCII);
        byte[] mp3 = "ID3.....".getBytes(StandardCharsets.US_ASCII);

        assertEquals(Optional.of("audio/webm"), AudioUploads.acceptedMediaType("audio/webm;codecs=opus", webm));
        assertEquals(Optional.of("audio/ogg"), AudioUploads.acceptedMediaType("audio/ogg; codecs=opus", ogg));
        assertEquals(Optional.of("audio/mp4"), AudioUploads.acceptedMediaType("audio/mp4", mp4), "Safari");
        assertEquals(Optional.of("audio/wav"), AudioUploads.acceptedMediaType("audio/wav", wav));
        assertEquals(Optional.of("audio/mpeg"), AudioUploads.acceptedMediaType("audio/mpeg", mp3));

        assertTrue(AudioUploads.acceptedMediaType("text/plain", webm).isEmpty(), "not an audio type");
        assertTrue(AudioUploads.acceptedMediaType("audio/webm", ogg).isEmpty(), "bytes do not match the type");
        assertTrue(AudioUploads.acceptedMediaType("audio/webm", "<html>".getBytes(StandardCharsets.US_ASCII)).isEmpty());
        assertTrue(AudioUploads.acceptedMediaType(null, webm).isEmpty());
    }

    @Test
    void textIsSplitAtSentenceEndsWithinTheLimit() {
        assertEquals(List.of("Short reply."), SpeechTextChunks.split("  Short reply.  ", 2500));
        assertEquals(List.of(), SpeechTextChunks.split("   ", 2500));

        String hindi = "आपकी दवा का समय हो गया है। ".repeat(120).strip();
        List<String> parts = SpeechTextChunks.split(hindi, 2500);
        assertTrue(parts.size() > 1);
        for (String part : parts) {
            assertTrue(part.length() <= 2500);
            assertTrue(part.endsWith("।"), "split after a danda");
        }
        assertEquals(hindi.replace(" ", ""), String.join("", parts).replace(" ", ""), "nothing lost");

        List<String> noSpaces = SpeechTextChunks.split("x".repeat(6000), 2500);
        assertEquals(List.of(2500, 2500, 1000), noSpaces.stream().map(String::length).toList());
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
