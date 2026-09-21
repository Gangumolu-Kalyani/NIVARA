package com.sih.nivara.service;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link AppUser} login accounts.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** All user accounts, unfiltered. */
    public List<AppUser> findAll() {
        return userRepository.findAll();
    }

    /** The user account with this id, or empty when none exists. */
    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    /** The user account with this public uuid, or empty when none exists. */
    public Optional<AppUser> findByUuid(UUID uuid) {
        return userRepository.findByUuid(uuid);
    }

    /** The account with this email, which must already be lowercase, or empty. */
    public Optional<AppUser> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /** Whether an account already uses this lowercase email. */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /** Inserts a new user account or updates an existing one. */
    @Transactional
    public AppUser save(AppUser user) {
        return userRepository.save(user);
    }

    /** Removes the user account with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }
}
