package com.sih.nivara.dto.response;

import java.time.Instant;

/**
 * A successful login: the access token to send as {@code Authorization: Bearer <accessToken>},
 * when it expires, and the account it authenticates. There is no refresh token; after
 * expiresAt the client logs in again.
 */
public record TokenResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        AccountResponse account) {
}
