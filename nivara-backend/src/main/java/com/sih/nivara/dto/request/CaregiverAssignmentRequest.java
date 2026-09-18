package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.RelationshipType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body of "give this caregiver access to this patient", writing one row of
 * table patient_caregivers (V1).
 *
 * <p>A request object is needed because relationship is NOT NULL with no column default,
 * so it cannot be derived from the patient and the caregiver alone. The patient comes from
 * the request path; accessLevel, primary and receivesAlerts are optional and fall back to
 * the column defaults EDITOR, false and true.
 *
 * <p>Who is allowed to send this is an authorization question, decided in a later phase.
 */
public record CaregiverAssignmentRequest(

        @NotNull
        UUID caregiverUserUuid,

        @NotNull
        RelationshipType relationship,

        AccessLevel accessLevel,

        Boolean primary,

        Boolean receivesAlerts) {
}
