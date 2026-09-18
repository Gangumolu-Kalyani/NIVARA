package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.CaregiverAssignmentRequest;
import com.sih.nivara.dto.response.PatientCaregiverResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PatientCaregiver;

/**
 * Explicit conversion between the caregiver-access DTOs and {@link PatientCaregiver}.
 *
 * <p>Pure field copying. The patient and the caregiver account are resolved by the caller
 * and passed in, because a mapper does not read the database.
 *
 * <p>{@link #toResponse} reads the patient and caregiver associations, which are lazy, so
 * call it inside the service transaction.
 */
public final class PatientCaregiverMapper {

    private PatientCaregiverMapper() {
        // utility class
    }

    /**
     * Builds a caregiver-access row. Optional flags the client left out are not written,
     * so the column defaults EDITOR, false and true survive.
     */
    public static PatientCaregiver toEntity(CaregiverAssignmentRequest request,
                                            Patient patient,
                                            AppUser caregiverUser) {
        PatientCaregiver link = new PatientCaregiver(patient, caregiverUser, request.relationship());
        if (request.accessLevel() != null) {
            link.setAccessLevel(request.accessLevel());
        }
        if (request.primary() != null) {
            link.setPrimary(request.primary());
        }
        if (request.receivesAlerts() != null) {
            link.setReceivesAlerts(request.receivesAlerts());
        }
        return link;
    }

    public static PatientCaregiverResponse toResponse(PatientCaregiver link) {
        AppUser caregiver = link.getCaregiverUser();
        return new PatientCaregiverResponse(
                link.getPatient().getUuid(),
                caregiver.getUuid(),
                caregiver.getFullName(),
                link.getRelationship(),
                link.getAccessLevel(),
                link.isPrimary(),
                link.isReceivesAlerts(),
                link.getCreatedAt(),
                link.getUpdatedAt());
    }
}
