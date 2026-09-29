package com.sih.nivara.assistant.speech;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Which recordings the assistant accepts: the formats browsers record in and Sarvam's
 * speech-to-text reads. WebM/Opus is what Chrome, Edge and Firefox record; Safari records MP4/AAC;
 * the others are common file formats.
 *
 * <p>The declared Content-Type is only a claim, so the first bytes must also look like that format.
 * A file that is not really audio is refused before anything is sent to a provider.
 */
public final class AudioUploads {

    /** Accepted media types, by their base type, to the format family their bytes must match. */
    private static final Map<String, Format> ACCEPTED = Map.ofEntries(
            Map.entry("audio/webm", Format.WEBM),
            Map.entry("video/webm", Format.WEBM),
            Map.entry("audio/ogg", Format.OGG),
            Map.entry("audio/opus", Format.OGG),
            Map.entry("audio/mp4", Format.MP4),
            Map.entry("audio/x-m4a", Format.MP4),
            Map.entry("audio/m4a", Format.MP4),
            Map.entry("audio/aac", Format.AAC),
            Map.entry("audio/mpeg", Format.MP3),
            Map.entry("audio/mp3", Format.MP3),
            Map.entry("audio/wav", Format.WAV),
            Map.entry("audio/x-wav", Format.WAV),
            Map.entry("audio/wave", Format.WAV));

    enum Format {
        WEBM, OGG, MP4, AAC, MP3, WAV
    }

    private AudioUploads() {
        // utility class
    }

    /**
     * The canonical media type to send to the provider, or empty when the declared type is not an
     * accepted audio type or the bytes do not look like it.
     */
    public static Optional<String> acceptedMediaType(String declaredContentType, byte[] bytes) {
        if (declaredContentType == null || bytes == null) {
            return Optional.empty();
        }
        String base = declaredContentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        Format format = ACCEPTED.get(base);
        if (format == null || !looksLike(format, bytes)) {
            return Optional.empty();
        }
        return Optional.of(switch (format) {
            case WEBM -> "audio/webm";
            case OGG -> "audio/ogg";
            case MP4 -> "audio/mp4";
            case AAC -> "audio/aac";
            case MP3 -> "audio/mpeg";
            case WAV -> "audio/wav";
        });
    }

    /** The file name extension the provider sees for a canonical media type. */
    static String extensionOf(String mediaType) {
        return switch (mediaType) {
            case "audio/webm" -> "webm";
            case "audio/ogg" -> "ogg";
            case "audio/mp4" -> "m4a";
            case "audio/aac" -> "aac";
            case "audio/mpeg" -> "mp3";
            default -> "wav";
        };
    }

    private static boolean looksLike(Format format, byte[] b) {
        return switch (format) {
            // EBML header, which WebM (Matroska) files start with
            case WEBM -> startsWith(b, 0, 0x1A, 0x45, 0xDF, 0xA3);
            case OGG -> startsWith(b, 0, 'O', 'g', 'g', 'S');
            // An ISO base media file: a box size, then "ftyp"
            case MP4 -> startsWith(b, 4, 'f', 't', 'y', 'p');
            // ADTS frame sync
            case AAC -> b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xF6) == 0xF0;
            // An ID3 tag, or an MPEG audio frame sync
            case MP3 -> startsWith(b, 0, 'I', 'D', '3') || (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0);
            case WAV -> startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'A', 'V', 'E');
        };
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
