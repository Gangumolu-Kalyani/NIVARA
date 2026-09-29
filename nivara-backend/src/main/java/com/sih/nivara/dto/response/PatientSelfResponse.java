package com.sih.nivara.dto.response;

import java.util.UUID;

/**
 * A patient's own record, as their own device sees it. Unlike {@link PatientResponse} it carries
 * no caregiver access level and nothing about the care team.
 */
public record PatientSelfResponse(
        UUID uuid,
        String fullName,
        String preferredName,
        String preferredLanguage,
        String timezone) {
}
