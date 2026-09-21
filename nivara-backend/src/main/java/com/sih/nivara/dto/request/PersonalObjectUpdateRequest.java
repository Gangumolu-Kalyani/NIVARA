package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.ObjectCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of "correct an object": a full replacement of the editable fields, so an omitted
 * nullable field is cleared. The NOT NULL columns of table personal_objects are required.
 *
 * <p>The patient is absent because personal_objects.patient_id is immutable.
 */
public record PersonalObjectUpdateRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        ObjectCategory category,

        @Size(max = 200)
        String usualLocation,

        String description,

        @NotNull
        Boolean includeInGames) {
}
