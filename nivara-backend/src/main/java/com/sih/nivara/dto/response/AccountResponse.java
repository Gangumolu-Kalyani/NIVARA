package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

/**
 * A login account as the API returns it. Identified by uuid, which is also the subject of the
 * account's access tokens.
 *
 * <p>The password hash, the bigint primary key and the enabled flag never leave the backend.
 */
public record AccountResponse(
        UUID uuid,
        String fullName,
        String email,
        String phone,
        UserRole role,
        String preferredLanguage,
        Instant lastLoginAt,
        Instant createdAt) {
}
