package com.sih.nivara.device.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Generates and hashes the two device credentials: the one-time pairing code a caregiver reads
 * out, and the long-lived secret the paired device keeps.
 *
 * <p>The pairing code is 8 characters from a 32-symbol alphabet without look-alikes (no I, O, 0
 * or 1): about 10^12 combinations, valid for 10 minutes, redeemable once. It is shown as
 * "ABCD-EFGH" and accepted in any case, with or without the hyphen or spaces. The device secret
 * is 32 random bytes. Both are high-entropy random values, not passwords, so a plain SHA-256 hash
 * is enough to store them; a slow password hash would add nothing.
 */
public final class DeviceCredentials {

    static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 8;
    private static final int SECRET_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceCredentials() {
        // utility class
    }

    /** A new pairing code, without formatting. */
    public static String newPairingCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }

    /** "ABCDEFGH" as "ABCD-EFGH", easier to read out and type. */
    public static String format(String code) {
        return code.substring(0, 4) + "-" + code.substring(4);
    }

    /**
     * A typed code in canonical form: upper case, hyphens and whitespace removed. Answers null
     * when the result cannot be a code, so a malformed input is rejected like a wrong one.
     */
    public static String normalize(String typed) {
        if (typed == null) {
            return null;
        }
        String code = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != CODE_LENGTH) {
            return null;
        }
        for (int i = 0; i < code.length(); i++) {
            if (CODE_ALPHABET.indexOf(code.charAt(i)) < 0) {
                return null;
            }
        }
        return code;
    }

    /** A new device secret, URL-safe base64 without padding. */
    public static String newSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256, the form stored in patient_devices. */
    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Whether a presented secret matches a stored hash, compared in constant time. */
    public static boolean matches(String presented, String storedHash) {
        if (presented == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(presented).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
