package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.PersonMapper;
import com.sih.nivara.dto.request.PersonUpdateRequest;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link Person} records in a patient's personal memory.
 * Repository delegation, plus the transactional update its REST API needs. No NIVARA
 * business rules yet.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class PersonService {

    private final PersonRepository personRepository;

    public PersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    /** All people, unfiltered. */
    public List<Person> findAll() {
        return personRepository.findAll();
    }

    /** The person with this id, or empty when none exists. */
    public Optional<Person> findById(Long id) {
        return personRepository.findById(id);
    }

    /**
     * The person with this public uuid, provided it belongs to this patient. Empty when it does
     * not exist or belongs to someone else, so callers cannot reach across patients.
     */
    public Optional<Person> findByUuidAndPatient(UUID uuid, Patient patient) {
        return personRepository.findByUuidAndPatient(uuid, patient);
    }

    /** The person with this public uuid, whichever patient it belongs to, or empty. */
    public Optional<Person> findByUuid(UUID uuid) {
        return personRepository.findByUuid(uuid);
    }

    /** One patient's people, by full name. */
    public List<Person> findByPatient(Patient patient) {
        return personRepository.findByPatientOrderByFullNameAscIdAsc(patient);
    }

    /**
     * Replaces the editable fields of an existing person, or reports empty when no person
     * has this uuid. Loading and changing it inside one transaction keeps the entity managed,
     * so the change is written by dirty checking and the patient fetched with it stays
     * initialised for the response. The patient itself cannot change.
     */
    @Transactional
    public Optional<Person> update(UUID uuid, PersonUpdateRequest request) {
        return personRepository.findByUuid(uuid).map(person -> {
            PersonMapper.applyUpdate(request, person);
            return person;
        });
    }

    /** Inserts a new person or updates an existing one. */
    @Transactional
    public Person save(Person person) {
        return personRepository.save(person);
    }
}
