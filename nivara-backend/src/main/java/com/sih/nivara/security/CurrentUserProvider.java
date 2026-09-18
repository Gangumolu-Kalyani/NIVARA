package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;

import java.util.Optional;

/**
 * Who is acting on this request. The single seam through which the rest of the backend
 * asks for the caller's account, so nothing else has to know how the caller is identified.
 *
 * <p>Today the only implementation is {@link DevelopmentCurrentUserProvider}, a
 * configuration-driven stand-in. When Spring Security is added, an implementation backed by
 * the SecurityContext replaces it and every caller of this interface keeps working unchanged.
 */
public interface CurrentUserProvider {

    /** The account acting on this request, or empty when no caller can be established. */
    Optional<AppUser> currentUser();
}
