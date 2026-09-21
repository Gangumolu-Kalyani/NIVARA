package com.sih.nivara.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of "create a caregiver account". Open self-registration: the account is always created as
 * a CAREGIVER, and there is deliberately no role field, so a client cannot ask for another role.
 *
 * <p>Bounds mirror table app_users (V1): the column lengths and the CHECK constraints
 * ck_app_users_full_name_not_blank, ck_app_users_email_format, ck_app_users_phone_format and
 * ck_app_users_preferred_language. The email is stored lowercase (ck_app_users_email_lowercase),
 * so it may be sent in any case.
 *
 * <p>The password bounds are policy, not schema: 8 characters as a minimum strength, and 72
 * because BCrypt cannot hash more than 72 bytes. The byte limit itself is enforced by the service,
 * since a character can take several bytes.
 */
public record RegisterRequest(

        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Size(max = 254)
        @Email
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+$")
        String email,

        @NotBlank
        @Size(min = 8, max = 72)
        String password,

        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$")
        String phone,

        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String preferredLanguage) {

    /** Keeps the password out of logs and exception messages that print the request. */
    @Override
    public String toString() {
        return "RegisterRequest[fullName=" + fullName + ", email=" + email + ", password=***, phone="
                + phone + ", preferredLanguage=" + preferredLanguage + "]";
    }
}
