package com.sih.nivara.assistant.speech;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Maps a conversation's language_code (BCP-47, such as {@code en}, {@code hi}, {@code hi-IN} or
 * {@code or-IN}) to the codes Sarvam's APIs accept, per its current documentation: the language
 * with {@code -IN}, and Odia as {@code od}.
 */
public final class SarvamLanguages {

    /** Saaras speech-to-text: 22 Indian languages and English. */
    static final Set<String> SPEECH_TO_TEXT = Set.of(
            "hi", "bn", "kn", "ml", "mr", "od", "pa", "ta", "te", "en", "gu", "as", "ur", "ne",
            "kok", "ks", "sd", "sa", "sat", "mni", "brx", "mai", "doi");

    /** Bulbul v3 text-to-speech: 10 Indian languages and English. */
    static final Set<String> TEXT_TO_SPEECH = Set.of(
            "bn", "en", "gu", "hi", "kn", "ml", "mr", "od", "pa", "ta", "te");

    /** What speech-to-text is told when the conversation's language is not one it knows: detect it. */
    static final String AUTO_DETECT = "unknown";

    private SarvamLanguages() {
        // utility class
    }

    /** The speech-to-text code for this language, or {@code unknown} so Sarvam detects it. */
    public static String forSpeechToText(String languageCode) {
        return sarvamCode(languageCode, SPEECH_TO_TEXT).orElse(AUTO_DETECT);
    }

    /** The text-to-speech code for this language, or empty when Bulbul cannot speak it. */
    public static Optional<String> forTextToSpeech(String languageCode) {
        return sarvamCode(languageCode, TEXT_TO_SPEECH);
    }

    private static Optional<String> sarvamCode(String languageCode, Set<String> supported) {
        if (languageCode == null || languageCode.isBlank()) {
            return Optional.empty();
        }
        String language = languageCode.strip().split("[-_]")[0].toLowerCase(Locale.ROOT);
        if (language.equals("or")) {
            // ISO 639-1 Odia; Sarvam names it od
            language = "od";
        }
        return supported.contains(language) ? Optional.of(language + "-IN") : Optional.empty();
    }
}
