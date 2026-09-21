package com.sih.nivara.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of "log in". Only presence is validated: any mismatch, whether an unknown email, a wrong
 * password or a disabled account, is the same 401, so the shape of a valid credential is never
 * revealed to a caller probing for accounts.
 */
public record LoginRequest(

        @NotBlank
        String email,

        @NotBlank
        String password) {

    /** Keeps the password out of logs and exception messages that print the request. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
