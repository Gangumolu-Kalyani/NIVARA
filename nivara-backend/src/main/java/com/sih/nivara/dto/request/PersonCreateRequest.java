package com.sih.nivara.dto.request;

import com.sih.nivara.entity.enums.RelationshipType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of "add someone the patient knows". The patient comes from the request path and the
 * creating account from the caller, so neither appears here.
 *
 * <p>Bounds mirror table people (V2): the column lengths and the CHECK constraints
 * ck_people_full_name_not_blank and ck_people_relationship. An omitted includeInGames keeps
 * the column default of true.
 */
public record PersonCreateRequest(

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

        Boolean includeInGames) {
}
