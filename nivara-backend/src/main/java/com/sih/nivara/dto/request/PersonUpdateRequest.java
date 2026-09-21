package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.RelationshipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of "correct a person": a full replacement of the editable fields, so an omitted
 * nullable field is cleared. The NOT NULL columns of table people are required.
 *
 * <p>The patient is absent because people.patient_id is immutable.
 */
public record PersonUpdateRequest(

        @NotBlank
        @Size(max = 120)
        String fullName,

        @Size(max = 60)
        String calledAs,

        @NotNull
        RelationshipType relationship,

        @Size(max = 60)
        String relationshipLabel,

        String description,

        @NotNull
        Boolean includeInGames) {
}
