package com.sih.nivara.assistant.speech;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a reply into parts a text-to-speech request accepts (Bulbul v3: 2,500 characters each),
 * at sentence ends where possible, then at spaces, and only as a last resort mid-word. The parts
 * are spoken in order, so the listener hears the whole reply.
 */
public final class SpeechTextChunks {

    private SpeechTextChunks() {
        // utility class
    }

    public static List<String> split(String text, int maxChars) {
        List<String> chunks = new ArrayList<>();
        String rest = text == null ? "" : text.strip();
        while (rest.length() > maxChars) {
            int cut = lastBreak(rest, maxChars);
            chunks.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            chunks.add(rest);
        }
        return chunks;
    }

    /** Where to end a part of at most maxChars: after a sentence end, else at a space, else at maxChars. */
    private static int lastBreak(String text, int maxChars) {
        for (int i = maxChars; i > maxChars / 2; i--) {
            char previous = text.charAt(i - 1);
            // Latin sentence ends, the Devanagari danda and double danda, and line breaks
            if ((previous == '.' || previous == '!' || previous == '?' || previous == '।' || previous == '॥'
                    || previous == '\n') && Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        for (int i = maxChars; i > maxChars / 2; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        // Never split a character that is stored as two UTF-16 units
        return Character.isLowSurrogate(text.charAt(maxChars)) ? maxChars - 1 : maxChars;
    }
}
