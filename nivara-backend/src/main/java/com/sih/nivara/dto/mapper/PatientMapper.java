package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.PatientCreateRequest;
import com.sih.nivara.dto.request.PatientUpdateRequest;
import com.sih.nivara.dto.response.PatientResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;

/**
 * Explicit conversion between the patient DTOs and {@link Patient}.
 *
 * <p>Pure field copying: no repository, no service, no validation, no business rule.
 * Anything the mapper needs beyond the request body, such as the account that owns the
 * new row, is passed in by the caller.
 *
 * <p>Call these inside the service transaction: reading a patient's lazy associations
 * outside one would fail, since open-in-view is disabled.
 */
public final class PatientMapper {

    private PatientMapper() {
        // utility class
    }

    /**
     * Builds a new patient from a create request. Optional fields that the client left out
     * are not written, so the entity and column defaults for cognitive stage, language and
     * time zone survive.
     */
    public static Patient toEntity(PatientCreateRequest request, AppUser createdByUser) {
        Patient patient = new Patient(request.fullName(), createdByUser);
        patient.setPreferredName(request.preferredName());
        patient.setBirthYear(request.birthYear());
        if (request.cognitiveStage() != null) {
            patient.setCognitiveStage(request.cognitiveStage());
        }
        if (request.preferredLanguage() != null) {
            patient.setPreferredLanguage(request.preferredLanguage());
        }
        if (request.timezone() != null) {
            patient.setTimezone(request.timezone());
        }
        return patient;
    }

    /** Copies a full-replacement update onto an existing patient. */
    public static void applyUpdate(PatientUpdateRequest request, Patient patient) {
        patient.setFullName(request.fullName());
        patient.setPreferredName(request.preferredName());
        patient.setBirthYear(request.birthYear());
        patient.setCognitiveStage(request.cognitiveStage());
        patient.setPreferredLanguage(request.preferredLanguage());
        patient.setTimezone(request.timezone());
    }

    public static PatientResponse toResponse(Patient patient) {
        return new PatientResponse(
                patient.getUuid(),
                patient.getFullName(),
                patient.getPreferredName(),
                patient.getBirthYear(),
                patient.getCognitiveStage(),
                patient.getPreferredLanguage(),
                patient.getTimezone(),
                patient.getCreatedAt(),
                patient.getUpdatedAt());
    }
}
