package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.CognitiveStage;
import com.sih.nivara.entity.enums.RelationshipType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of "create a patient". Every field here is client-supplied; the identifiers,
 * timestamps, version and owning account are set by the database or the service layer.
 *
 * <p>Bounds mirror table patients (V1): column lengths and the CHECK constraints
 * ck_patients_full_name_not_blank, ck_patients_birth_year and ck_patients_preferred_language.
 * A null optional field means "use the column default", not "clear it".
 *
 * <p>relationship is how the creating caregiver relates to the patient. The creator becomes the
 * patient's OWNER and primary caregiver; when relationship is omitted it is recorded as CAREGIVER.
 */
public record PatientCreateRequest(

        @NotBlank
        @Size(max = 120)
        String fullName,

        @Size(max = 60)
        String preferredName,

        @Min(1900)
        @Max(2100)
        Short birthYear,

        CognitiveStage cognitiveStage,

        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String preferredLanguage,

        @Size(max = 40)
        String timezone,

        RelationshipType relationship) {
}
