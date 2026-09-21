package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.ObjectCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of "add an everyday object the patient uses". The patient comes from the request path
 * and the creating account from the caller, so neither appears here.
 *
 * <p>Bounds mirror table personal_objects (V2): the column lengths and the CHECK constraints
 * ck_personal_objects_name_not_blank and ck_personal_objects_category. An omitted
 * includeInGames keeps the column default of true.
 */
public record PersonalObjectCreateRequest(

        @NotBlank
        @Size(max = 120)
        String name,

        @NotNull
        ObjectCategory category,

        @Size(max = 200)
        String usualLocation,

        String description,

        Boolean includeInGames) {
}
