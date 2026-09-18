package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.MemoryMapper;
import com.sih.nivara.dto.request.MemoryCreateRequest;
import com.sih.nivara.dto.request.MemoryUpdateRequest;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.entity.Place;
import com.sih.nivara.repository.MemoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service access to {@link Memory} records in a patient's personal memory.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 *
 * <p>Unlike the phase 5B services, the two write methods take request DTOs. Writing a memory
 * means resolving its place, people and objects and saving the links in one transaction, and
 * that resolution is exactly the part that must not leak into a controller.
 *
 * <p>Every reference is resolved within the memory's own patient. The database cannot express
 * that rule, so it is enforced here: a uuid belonging to another patient is reported as not
 * found, like any other unknown reference.
 */
@Service
@Transactional(readOnly = true)
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final PlaceService placeService;
    private final PersonService personService;
    private final PersonalObjectService personalObjectService;

    public MemoryService(MemoryRepository memoryRepository,
                         PlaceService placeService,
                         PersonService personService,
                         PersonalObjectService personalObjectService) {
        this.memoryRepository = memoryRepository;
        this.placeService = placeService;
        this.personService = personService;
        this.personalObjectService = personalObjectService;
    }

    /** All memories, unfiltered. */
    public List<Memory> findAll() {
        return memoryRepository.findAll();
    }

    /** The memory with this id, or empty when none exists. */
    public Optional<Memory> findById(Long id) {
        return memoryRepository.findById(id);
    }

    /** The memory with this public uuid, or empty when none exists. */
    public Optional<Memory> findByUuid(UUID uuid) {
        return memoryRepository.findByUuid(uuid);
    }

    /** One patient's memories, most recent first. */
    public List<Memory> findByPatient(Patient patient) {
        return memoryRepository.findByPatientOrderByOccurredOnDescIdDesc(patient);
    }

    /** Inserts a new memory or updates an existing one. */
    @Transactional
    public Memory save(Memory memory) {
        return memoryRepository.save(memory);
    }

    /**
     * Records a memory for this patient, resolving and linking its place, people and objects
     * in the same transaction. The recording account is the caller, never the request body.
     */
    @Transactional
    public Memory create(Patient patient, MemoryCreateRequest request, AppUser recordedByUser) {
        Place place = resolvePlace(patient, request.placeUuid());
        Set<Person> people = resolvePeople(patient, request.peopleUuids());
        Set<PersonalObject> objects = resolveObjects(patient, request.objectUuids());

        Memory memory = MemoryMapper.toEntity(request, patient, recordedByUser, place, people, objects);
        return memoryRepository.save(memory);
    }

    /**
     * Replaces the editable fields and the links of an existing memory, or reports empty when
     * no memory has this uuid. The memory is loaded and changed inside one transaction, so the
     * link rows are rewritten against a managed entity rather than merged from a detached one.
     */
    @Transactional
    public Optional<Memory> update(UUID uuid, MemoryUpdateRequest request) {
        return memoryRepository.findByUuid(uuid).map(memory -> {
            Patient patient = memory.getPatient();
            Place place = resolvePlace(patient, request.placeUuid());
            Set<Person> people = resolvePeople(patient, request.peopleUuids());
            Set<PersonalObject> objects = resolveObjects(patient, request.objectUuids());

            MemoryMapper.applyUpdate(request, memory, place, people, objects);
            return memory;
        });
    }

    private Place resolvePlace(Patient patient, UUID placeUuid) {
        if (placeUuid == null) {
            return null;
        }
        return placeService.findByUuidAndPatient(placeUuid, patient)
                .orElseThrow(() -> notFound("place", placeUuid));
    }

    private Set<Person> resolvePeople(Patient patient, Set<UUID> peopleUuids) {
        Set<Person> people = new LinkedHashSet<>();
        if (peopleUuids == null) {
            return people;
        }
        for (UUID personUuid : peopleUuids) {
            people.add(personService.findByUuidAndPatient(personUuid, patient)
                    .orElseThrow(() -> notFound("person", personUuid)));
        }
        return people;
    }

    private Set<PersonalObject> resolveObjects(Patient patient, Set<UUID> objectUuids) {
        Set<PersonalObject> objects = new LinkedHashSet<>();
        if (objectUuids == null) {
            return objects;
        }
        for (UUID objectUuid : objectUuids) {
            objects.add(personalObjectService.findByUuidAndPatient(objectUuid, patient)
                    .orElseThrow(() -> notFound("object", objectUuid)));
        }
        return objects;
    }

    /**
     * Reports an unresolved reference the way the rest of the project reports a missing
     * resource, rather than introducing a second error mechanism for one phase.
     */
    private static ResponseStatusException notFound(String what, UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No " + what + " " + uuid + " belonging to this patient");
    }
}
