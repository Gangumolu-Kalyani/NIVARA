package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.PlaceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of "add a place that matters to the patient". The patient comes from the request path
 * and the creating account from the caller, so neither appears here.
 *
 * <p>Bounds mirror table places (V2): the column lengths and the CHECK constraints
 * ck_places_name_not_blank and ck_places_place_type. An omitted includeInGames keeps the
 * column default of true.
 */
public record PlaceCreateRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        PlaceType placeType,

        String description,

        Boolean includeInGames) {
}
