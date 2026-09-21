package com.sih.nivara.security;

import com.sih.nivara.entity.AppUser;

import java.util.Optional;

/**
 * Who is acting on this request. The single seam through which the rest of the backend
 * asks for the caller's account, so nothing else has to know how the caller is identified.
 *
 * <p>The implementation is {@link SecurityContextCurrentUserProvider}: the account that
 * authenticated the request with its access token. Controllers written against this interface
 * before authentication existed kept working unchanged when it arrived.
 */
public interface CurrentUserProvider {

    /** The account acting on this request, or empty when the request is not authenticated. */
    Optional<AppUser> currentUser();
}
