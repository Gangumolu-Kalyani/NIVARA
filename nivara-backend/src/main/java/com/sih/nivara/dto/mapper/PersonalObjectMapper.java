package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.PersonalObjectCreateRequest;
import com.sih.nivara.dto.request.PersonalObjectUpdateRequest;
import com.sih.nivara.dto.response.PersonalObjectResponse;
import com.sih.nivara.dto.response.PersonalObjectSummaryResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PersonalObject;

/**
 * Explicit conversion between the objects DTOs and {@link PersonalObject}.
 *
 * <p>Pure field copying: no repository, no service, no rule. The patient and the creating
 * account are resolved by the caller and passed in.
 *
 * <p>{@link #toResponse} reads the lazy patient association, so the object must have been
 * loaded with it fetched or be mapped inside the transaction that loaded it.
 */
public final class PersonalObjectMapper {

    private PersonalObjectMapper() {
        // utility class
    }

    /** Builds a new object. An omitted includeInGames keeps the default of true. */
    public static PersonalObject toEntity(PersonalObjectCreateRequest request,
                                          Patient patient,
                                          AppUser createdByUser) {
        PersonalObject personalObject =
                new PersonalObject(patient, request.name(), request.category(), createdByUser);
        personalObject.setUsualLocation(request.usualLocation());
        personalObject.setDescription(request.description());
        if (request.includeInGames() != null) {
            personalObject.setIncludeInGames(request.includeInGames());
        }
        return personalObject;
    }

    /** Copies a full-replacement update onto an existing object. */
    public static void applyUpdate(PersonalObjectUpdateRequest request, PersonalObject personalObject) {
        personalObject.setName(request.name());
        personalObject.setCategory(request.category());
        personalObject.setUsualLocation(request.usualLocation());
        personalObject.setDescription(request.description());
        personalObject.setIncludeInGames(request.includeInGames());
    }

    public static PersonalObjectResponse toResponse(PersonalObject personalObject) {
        return new PersonalObjectResponse(
                personalObject.getUuid(),
                personalObject.getPatient().getUuid(),
                personalObject.getName(),
                personalObject.getCategory(),
                personalObject.getUsualLocation(),
                personalObject.getDescription(),
                personalObject.isIncludeInGames(),
                personalObject.getCreatedAt(),
                personalObject.getUpdatedAt());
    }

    /** The short form a memory embeds for each object involved. */
    public static PersonalObjectSummaryResponse toSummary(PersonalObject personalObject) {
        return new PersonalObjectSummaryResponse(
                personalObject.getUuid(),
                personalObject.getName(),
                personalObject.getCategory());
    }
}
