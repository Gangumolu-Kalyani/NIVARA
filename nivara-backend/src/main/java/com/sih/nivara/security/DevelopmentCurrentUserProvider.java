package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Development stand-in for the authenticated principal, used until Spring Security exists.
 *
 * <p>It resolves one account named by the server-side property
 * {@code nivara.security.development-user-uuid}. That property is deployment configuration,
 * never part of the HTTP contract: no client can choose, send or discover the acting account.
 * Unset, which is the default, it reports no caller, and endpoints that need one refuse.
 *
 * <p>Delete this class when the SecurityContext-backed provider arrives; nothing else changes.
 */
@Component
public class DevelopmentCurrentUserProvider implements CurrentUserProvider {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentCurrentUserProvider.class);

    private final UserService userService;
    private final UUID developmentUserUuid;

    public DevelopmentCurrentUserProvider(UserService userService,
                                          @Value("${nivara.security.development-user-uuid:}") String configuredUuid) {
        this.userService = userService;
        this.developmentUserUuid = parse(configuredUuid);
        if (developmentUserUuid == null) {
            log.info("No development user configured: endpoints that need a caller will refuse "
                    + "until Spring Security provides the authenticated principal.");
        } else {
            log.warn("Acting as development user {}. This stand-in is replaced by the "
                    + "authenticated principal when Spring Security is added.", developmentUserUuid);
        }
    }

    @Override
    public Optional<AppUser> currentUser() {
        return developmentUserUuid == null
                ? Optional.empty()
                : userService.findByUuid(developmentUserUuid);
    }

    private static UUID parse(String configuredUuid) {
        if (configuredUuid == null || configuredUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(configuredUuid.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring nivara.security.development-user-uuid: '{}' is not a uuid.", configuredUuid);
            return null;
        }
    }
}
