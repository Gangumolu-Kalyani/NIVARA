package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.AccountMapper;
import com.sih.nivara.dto.request.LoginRequest;
import com.sih.nivara.dto.request.RegisterRequest;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.enums.UserRole;
import com.sih.nivara.security.JwtTokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/**
 * Registration and login.
 *
 * <p>Registration always creates a CAREGIVER account; the role is decided here, never by the
 * client. Emails are stored and matched in lowercase, so login is case-insensitive.
 *
 * <p>Login fails the same way whatever went wrong, whether the email is unknown, the password is
 * wrong or the account is disabled, so a caller cannot learn which emails have accounts. For the
 * same reason an unknown email still costs one BCrypt comparison against a dummy hash, keeping its
 * response time in line with a wrong password.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    /** BCrypt hashes at most 72 bytes of input; longer passwords cannot be verified reliably. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final String dummyHash;

    /** A successful login: the account and its freshly issued access token. */
    public record Login(AppUser account, String accessToken, Instant expiresAt) {
    }

    public AuthService(UserService userService, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.dummyHash = passwordEncoder.encode("nivara-timing-equalizer-not-a-real-password");
    }

    /**
     * Creates a CAREGIVER account with a BCrypt-hashed password. 409 when the email is already
     * registered in any letter case, including when two registrations race for the same email.
     */
    @Transactional
    public AppUser register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "password must be at most " + MAX_PASSWORD_BYTES + " bytes");
        }
        String email = normalize(request.email());
        if (userService.existsByEmail(email)) {
            throw emailTaken();
        }
        AppUser account = AccountMapper.toEntity(request, email, passwordEncoder.encode(request.password()),
                UserRole.CAREGIVER);
        try {
            return userService.save(account);
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("uq_app_users_email")) {
                throw emailTaken();
            }
            throw ex;
        }
    }

    /**
     * Verifies the credentials, records the login time and issues an access token. Any failure is
     * the same generic 401.
     */
    @Transactional
    public Login login(LoginRequest request) {
        Optional<AppUser> found = userService.findByEmail(normalize(request.email()));
        boolean passwordMatches = matches(request.password(), found.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordMatches || !found.get().isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        AppUser account = found.get();
        account.setLastLoginAt(Instant.now());
        JwtTokenService.IssuedToken token = jwtTokenService.issue(account);
        return new Login(account, token.value(), token.expiresAt());
    }

    /**
     * Compares without ever throwing: a password longer than BCrypt can hash, or a stored hash in an
     * unrecognized format, is simply not a match. The comparison still runs, keeping timing even.
     */
    private boolean matches(String rawPassword, String storedHash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            passwordEncoder.matches("x", dummyHash);
            return false;
        }
        try {
            return passwordEncoder.matches(rawPassword, storedHash);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static String normalize(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException emailTaken() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
