package com.sih.nivara.dto.mapper;

import com.sih.nivara.dto.request.MemoryCreateRequest;
import com.sih.nivara.dto.request.MemoryUpdateRequest;
import com.sih.nivara.dto.response.MemoryResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.entity.Place;

import java.util.Set;

/**
 * Explicit conversion between the memory DTOs and {@link Memory}.
 *
 * <p>Pure field copying: no repository, no service, no rule. The patient, the recording
 * account and the referenced place, people and objects are resolved by the caller and passed
 * in already checked, because a mapper does not read the database.
 *
 * <p>{@link #toResponse} reads the patient, place, people and objects associations, which are
 * lazy, so the memory must have been loaded with them fetched or be mapped inside the
 * transaction that loaded it.
 *
 * <p>The embedded place, people and objects summaries come from {@link PlaceMapper},
 * {@link PersonMapper} and {@link PersonalObjectMapper}, which own those types.
 */
public final class MemoryMapper {

    private MemoryMapper() {
        // utility class
    }

    /**
     * Builds a new memory. An omitted includeInGames is not written, so the entity and column
     * default of true survives.
     */
    public static Memory toEntity(MemoryCreateRequest request,
                                  Patient patient,
                                  AppUser recordedByUser,
                                  Place place,
                                  Set<Person> people,
                                  Set<PersonalObject> objects) {
        Memory memory = new Memory(patient, request.title(), request.description(), request.memoryType(),
                request.occurredOn(), request.languageCode(), request.source());
        memory.setTimeOfDay(request.timeOfDay());
        memory.setPlace(place);
        memory.setRecordedByUser(recordedByUser);
        if (request.includeInGames() != null) {
            memory.setIncludeInGames(request.includeInGames());
        }
        people.forEach(memory::addPerson);
        objects.forEach(memory::addObject);
        return memory;
    }

    /**
     * Copies a full-replacement update onto an existing memory. The link sets are emptied and
     * refilled in place rather than replaced, so Hibernate deletes and inserts only the
     * memory_people and memory_objects rows that actually changed.
     */
    public static void applyUpdate(MemoryUpdateRequest request,
                                   Memory memory,
                                   Place place,
                                   Set<Person> people,
                                   Set<PersonalObject> objects) {
        memory.setTitle(request.title());
        memory.setDescription(request.description());
        memory.setMemoryType(request.memoryType());
        memory.setOccurredOn(request.occurredOn());
        memory.setTimeOfDay(request.timeOfDay());
        memory.setPlace(place);
        memory.setLanguageCode(request.languageCode());
        memory.setIncludeInGames(request.includeInGames());

        memory.getPeople().clear();
        people.forEach(memory::addPerson);

        memory.getObjects().clear();
        objects.forEach(memory::addObject);
    }

    public static MemoryResponse toResponse(Memory memory) {
        return new MemoryResponse(
                memory.getUuid(),
                memory.getPatient().getUuid(),
                memory.getTitle(),
                memory.getDescription(),
                memory.getMemoryType(),
                memory.getOccurredOn(),
                memory.getTimeOfDay(),
                memory.getPlace() == null ? null : PlaceMapper.toSummary(memory.getPlace()),
                memory.getLanguageCode(),
                memory.getSource(),
                memory.isIncludeInGames(),
                memory.getPeople().stream().map(PersonMapper::toSummary).toList(),
                memory.getObjects().stream().map(PersonalObjectMapper::toSummary).toList(),
                memory.getCreatedAt(),
                memory.getUpdatedAt());
    }
}
