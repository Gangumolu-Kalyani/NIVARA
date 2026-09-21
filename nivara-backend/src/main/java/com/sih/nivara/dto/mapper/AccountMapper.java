package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.RegisterRequest;
import com.sih.nivara.dto.response.AccountResponse;
import com.sih.nivara.dto.response.TokenResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.enums.UserRole;

import java.time.Instant;

/**
 * Explicit conversion between the account DTOs and {@link AppUser}.
 *
 * <p>Pure field copying: the normalized email, the password hash and the role are decided by the
 * caller and passed in, because normalizing, hashing and choosing a role are not a mapper's job.
 * The plaintext password in the request is never read here.
 */
public final class AccountMapper {

    private AccountMapper() {
        // utility class
    }

    /** Builds a new account. An omitted preferredLanguage keeps the default of "en". */
    public static AppUser toEntity(RegisterRequest request, String normalizedEmail, String passwordHash,
                                   UserRole role) {
        AppUser account = new AppUser(request.fullName(), normalizedEmail, passwordHash, role);
        account.setPhone(request.phone());
        if (request.preferredLanguage() != null) {
            account.setPreferredLanguage(request.preferredLanguage());
        }
        return account;
    }

    public static AccountResponse toResponse(AppUser account) {
        return new AccountResponse(
                account.getUuid(),
                account.getFullName(),
                account.getEmail(),
                account.getPhone(),
                account.getRole(),
                account.getPreferredLanguage(),
                account.getLastLoginAt(),
                account.getCreatedAt());
    }

    public static TokenResponse toTokenResponse(String accessToken, Instant expiresAt, AppUser account) {
        return new TokenResponse(accessToken, "Bearer", expiresAt, toResponse(account));
    }
}
