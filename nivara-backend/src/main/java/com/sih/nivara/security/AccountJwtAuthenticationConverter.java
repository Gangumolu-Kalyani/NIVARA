package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.service.UserService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Turns a verified access token into an authenticated account.
 *
 * <p>By the time this runs, the signature, expiry and issuer have been checked. That is not
 * enough on its own: a correctly signed token can outlive its account. So the account named by
 * the subject is loaded, and a token whose account no longer exists or has been disabled is
 * rejected with 401, exactly like a forged one.
 *
 * <p>The authority comes from the account's current role in the database, not from the token's
 * role claim, so a role change takes effect without waiting for the token to expire.
 */
@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;

    public AccountJwtAuthenticationConverter(UserService userService) {
        this.userService = userService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser account = parseSubject(jwt.getSubject())
                .flatMap(userService::findByUuid)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new InvalidBearerTokenException("Account is unknown or disabled"));
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name())),
                account.getUuid().toString());
    }

    private static Optional<UUID> parseSubject(String subject) {
        try {
            return subject == null ? Optional.empty() : Optional.of(UUID.fromString(subject));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
