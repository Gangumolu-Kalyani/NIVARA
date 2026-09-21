package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.PersonalObjectMapper;
import com.sih.nivara.dto.request.PersonalObjectUpdateRequest;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.repository.PersonalObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link PersonalObject} records in a patient's personal memory.
 * Repository delegation, plus the transactional update its REST API needs. No NIVARA
 * business rules yet.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class PersonalObjectService {

    private final PersonalObjectRepository personalObjectRepository;

    public PersonalObjectService(PersonalObjectRepository personalObjectRepository) {
        this.personalObjectRepository = personalObjectRepository;
    }

    /** All personal objects, unfiltered. */
    public List<PersonalObject> findAll() {
        return personalObjectRepository.findAll();
    }

    /** The personal object with this id, or empty when none exists. */
    public Optional<PersonalObject> findById(Long id) {
        return personalObjectRepository.findById(id);
    }

    /**
     * The personal object with this public uuid, provided it belongs to this patient. Empty when it does
     * not exist or belongs to someone else, so callers cannot reach across patients.
     */
    public Optional<PersonalObject> findByUuidAndPatient(UUID uuid, Patient patient) {
        return personalObjectRepository.findByUuidAndPatient(uuid, patient);
    }

    /** The personal object with this public uuid, whichever patient it belongs to, or empty. */
    public Optional<PersonalObject> findByUuid(UUID uuid) {
        return personalObjectRepository.findByUuid(uuid);
    }

    /** One patient's personal objects, by name. */
    public List<PersonalObject> findByPatient(Patient patient) {
        return personalObjectRepository.findByPatientOrderByNameAscIdAsc(patient);
    }

    /**
     * Replaces the editable fields of an existing personal object, or reports empty when no personal object
     * has this uuid. Loading and changing it inside one transaction keeps the entity managed,
     * so the change is written by dirty checking and the patient fetched with it stays
     * initialised for the response. The patient itself cannot change.
     */
    @Transactional
    public Optional<PersonalObject> update(UUID uuid, PersonalObjectUpdateRequest request) {
        return personalObjectRepository.findByUuid(uuid).map(personalObject -> {
            PersonalObjectMapper.applyUpdate(request, personalObject);
            return personalObject;
        });
    }

    /** Inserts a new personal object or updates an existing one. */
    @Transactional
    public PersonalObject save(PersonalObject personalObject) {
        return personalObjectRepository.save(personalObject);
    }
}
