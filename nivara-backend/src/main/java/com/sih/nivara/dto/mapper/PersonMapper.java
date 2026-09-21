package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.PersonCreateRequest;
import com.sih.nivara.dto.request.PersonUpdateRequest;
import com.sih.nivara.dto.response.PersonResponse;
import com.sih.nivara.dto.response.PersonSummaryResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;

/**
 * Explicit conversion between the people DTOs and {@link Person}.
 *
 * <p>Pure field copying: no repository, no service, no rule. The patient and the creating
 * account are resolved by the caller and passed in.
 *
 * <p>{@link #toResponse} reads the lazy patient association, so the person must have been
 * loaded with it fetched or be mapped inside the transaction that loaded it.
 */
public final class PersonMapper {

    private PersonMapper() {
        // utility class
    }

    /** Builds a new person. An omitted includeInGames keeps the default of true. */
    public static Person toEntity(PersonCreateRequest request, Patient patient, AppUser createdByUser) {
        Person person = new Person(patient, request.fullName(), request.relationship(), createdByUser);
        person.setCalledAs(request.calledAs());
        person.setRelationshipLabel(request.relationshipLabel());
        person.setDescription(request.description());
        if (request.includeInGames() != null) {
            person.setIncludeInGames(request.includeInGames());
        }
        return person;
    }

    /** Copies a full-replacement update onto an existing person. */
    public static void applyUpdate(PersonUpdateRequest request, Person person) {
        person.setFullName(request.fullName());
        person.setCalledAs(request.calledAs());
        person.setRelationship(request.relationship());
        person.setRelationshipLabel(request.relationshipLabel());
        person.setDescription(request.description());
        person.setIncludeInGames(request.includeInGames());
    }

    public static PersonResponse toResponse(Person person) {
        return new PersonResponse(
                person.getUuid(),
                person.getPatient().getUuid(),
                person.getFullName(),
                person.getCalledAs(),
                person.getRelationship(),
                person.getRelationshipLabel(),
                person.getDescription(),
                person.isIncludeInGames(),
                person.getCreatedAt(),
                person.getUpdatedAt());
    }

    /** The short form a memory embeds for each person involved. */
    public static PersonSummaryResponse toSummary(Person person) {
        return new PersonSummaryResponse(
                person.getUuid(),
                person.getFullName(),
                person.getCalledAs(),
                person.getRelationship());
    }
}
