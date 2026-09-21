package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The acting account is the one that authenticated this request with its access token.
 *
 * <p>{@link AccountJwtAuthenticationConverter} has already confirmed that the token's subject names
 * an existing, enabled account, so for any request that reached a controller this finds it. It
 * reports empty only when the request is not authenticated with a bearer token at all.
 */
@Component
public class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    private final UserService userService;

    public SecurityContextCurrentUserProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Optional<AppUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !token.isAuthenticated()) {
            return Optional.empty();
        }
        return userService.findByUuid(UUID.fromString(token.getToken().getSubject()));
    }
}
