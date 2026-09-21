package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.AccountMapper;
import com.sih.nivara.dto.request.LoginRequest;
import com.sih.nivara.dto.request.RegisterRequest;
import com.sih.nivara.dto.response.AccountResponse;
import com.sih.nivara.dto.response.TokenResponse;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

/**
 * Account REST API: caregiver self-registration, login, and the current account.
 *
 * <p>register and login are the only public endpoints besides the health check; me, like the rest
 * of the API, needs a bearer token. No entity crosses this boundary; {@link AccountMapper} converts
 * both ways, and the password hash never appears in a response.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CurrentUserProvider currentUserProvider;

    public AuthController(AuthService authService, CurrentUserProvider currentUserProvider) {
        this.authService = authService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Creates a caregiver account. Answers 201 with the account, 400 for an invalid request, or 409
     * when the email is already registered. A role in the body is ignored: the account is always a
     * CAREGIVER.
     */
    @PostMapping("/register")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        AccountResponse body = AccountMapper.toResponse(authService.register(request));
        return ResponseEntity.created(URI.create("/api/auth/me")).body(body);
    }

    /** Exchanges credentials for an access token. Any failure is the same 401. */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.Login login = authService.login(request);
        return AccountMapper.toTokenResponse(login.accessToken(), login.expiresAt(), login.account());
    }

    /** The account the request's access token belongs to. */
    @GetMapping("/me")
    public AccountResponse me() {
        return AccountMapper.toResponse(currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated")));
    }
}
