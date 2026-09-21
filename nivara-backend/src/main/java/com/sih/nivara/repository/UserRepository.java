package com.sih.nivara.repository;

import com.sih.nivara.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link AppUser} login accounts (table app_users).
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {

    /**
     * Looks an account up by the public identifier the API uses. Backed by the unique
     * constraint uq_app_users_uuid (V1), so it matches at most one row.
     */
    Optional<AppUser> findByUuid(UUID uuid);

    /**
     * Looks an account up by its email, which must already be lowercase: the column only holds
     * lowercase values (ck_app_users_email_lowercase). Backed by uq_app_users_email.
     */
    Optional<AppUser> findByEmail(String email);

    /** Whether an account already uses this lowercase email. */
    boolean existsByEmail(String email);
}
