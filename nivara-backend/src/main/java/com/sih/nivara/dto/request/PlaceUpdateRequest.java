package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.PlaceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of "correct a place": a full replacement of the editable fields, so an omitted
 * nullable field is cleared. The NOT NULL columns of table places are required.
 *
 * <p>The patient is absent because places.patient_id is immutable.
 */
public record PlaceUpdateRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        PlaceType placeType,

        String description,

        @NotNull
        Boolean includeInGames) {
}
