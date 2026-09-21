package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.RelationshipType;
import jakarta.validation.constraints.NotNull;

/**
 * Body of "change a caregiver's access to this patient": a full replacement of the editable
 * columns of one patient_caregivers row (V1). The patient and the caregiver come from the request
 * path; neither can be changed, because the link itself is what they identify.
 *
 * <p>Every column is NOT NULL, so every field is required. Setting primary to true moves the
 * primary role to this caregiver; it cannot be set to false on the current primary, because a
 * patient keeps exactly one.
 */
public record CaregiverAccessUpdateRequest(

        @NotNull
        AccessLevel accessLevel,

        @NotNull
        RelationshipType relationship,

        @NotNull
        Boolean primary,

        @NotNull
        Boolean receivesAlerts) {
}
