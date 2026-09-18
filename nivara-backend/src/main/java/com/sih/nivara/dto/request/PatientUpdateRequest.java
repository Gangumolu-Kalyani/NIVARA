package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.CognitiveStage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of "update a patient's profile": a full replacement of the editable fields,
 * so a nullable field left out is cleared.
 *
 * <p>The columns that are NOT NULL in table patients are required here, because a
 * replacement cannot leave them empty. Only preferredName and birthYear are nullable
 * in the schema and therefore optional.
 *
 * <p>The patient is identified by the request path, not by this body.
 */
public record PatientUpdateRequest(

        @NotBlank
        @Size(max = 120)
        String fullName,

        @Size(max = 60)
        String preferredName,

        @Min(1900)
        @Max(2100)
        Short birthYear,

        @NotNull
        CognitiveStage cognitiveStage,

        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$")
        String preferredLanguage,

        @NotBlank
        @Size(max = 40)
        String timezone) {
}
