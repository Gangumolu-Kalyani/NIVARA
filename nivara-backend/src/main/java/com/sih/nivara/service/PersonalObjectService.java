package com.sih.nivara.service;

import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.repository.PersonalObjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link PersonalObject} records in a patient's personal memory.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
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

    /** Inserts a new personal object or updates an existing one. */
    @Transactional
    public PersonalObject save(PersonalObject personalObject) {
        return personalObjectRepository.save(personalObject);
    }
}
