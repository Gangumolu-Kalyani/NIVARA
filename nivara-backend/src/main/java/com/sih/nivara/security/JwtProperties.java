package com.sih.nivara.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Access-token settings, bound from {@code nivara.security.jwt.*}.
 *
 * <p>secret comes from the JWT_SECRET environment variable and has no default: the application
 * refuses to start without one, rather than sign tokens with a key anybody could know. HS256
 * needs a key of at least 256 bits, so a shorter secret is rejected at startup too.
 *
 * @param secret the HMAC key, used as its UTF-8 bytes
 * @param ttl    how long an access token stays valid
 */
@Validated
@ConfigurationProperties(prefix = "nivara.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration ttl) {

    /** 256 bits, the minimum key size for HS256. */
    public static final int MINIMUM_SECRET_BYTES = 32;

    /**
     * Fails startup with a clear reason. An unset JWT_SECRET reaches this binding as the literal,
     * unresolved text "${JWT_SECRET}", so that case is recognized explicitly rather than being
     * reported as a merely short secret.
     */
    public JwtProperties {
        if (secret == null || secret.isBlank() || secret.startsWith("${")) {
            throw new IllegalArgumentException("JWT_SECRET is not set. Set it to at least "
                    + MINIMUM_SECRET_BYTES + " random bytes; the application does not start without it");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT_SECRET must be at least " + MINIMUM_SECRET_BYTES
                    + " bytes (256 bits) for HS256");
        }
    }
}
