package com.sih.nivara.security;

import com.sih.nivara.device.service.DeviceSessionValidator;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.enums.UserRole;
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
 *
 * <p>A PATIENT account has no password: its tokens are issued to a paired device and carry that
 * device's uuid. Such a token is accepted only while the device is still active, so revoking a
 * device ends its session at once rather than when the token expires. A PATIENT token without a
 * device is rejected.
 */
@Component
public class AccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserService userService;
    private final DeviceSessionValidator deviceSessionValidator;

    public AccountJwtAuthenticationConverter(UserService userService, DeviceSessionValidator deviceSessionValidator) {
        this.userService = userService;
        this.deviceSessionValidator = deviceSessionValidator;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AppUser account = parseSubject(jwt.getSubject())
                .flatMap(userService::findByUuid)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new InvalidBearerTokenException("Account is unknown or disabled"));
        if (account.getRole() == UserRole.PATIENT) {
            boolean deviceActive = parseSubject(jwt.getClaimAsString(JwtTokenService.DEVICE_CLAIM))
                    .map(deviceUuid -> deviceSessionValidator.isActiveFor(deviceUuid, account))
                    .orElse(false);
            if (!deviceActive) {
                throw new InvalidBearerTokenException("This device is no longer signed in");
            }
        }
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
