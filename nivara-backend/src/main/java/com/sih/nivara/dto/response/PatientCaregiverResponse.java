package com.sih.nivara.dto.response;

import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.RelationshipType;

import java.time.Instant;
import java.util.UUID;

/**
 * One caregiver's access to one patient, as the API returns it.
 *
 * <p>The caregiver is identified by uuid and full name only. Nothing else from app_users
 * is carried here: no password hash, no email, no role, no login timestamps.
 */
public record PatientCaregiverResponse(
        UUID patientUuid,
        UUID caregiverUserUuid,
        String caregiverFullName,
        RelationshipType relationship,
        AccessLevel accessLevel,
        boolean primary,
        boolean receivesAlerts,
        Instant createdAt,
        Instant updatedAt) {
}
