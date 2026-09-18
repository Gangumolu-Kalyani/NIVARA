package com.sih.nivara.service;

import com.sih.nivara.entity.Person;
import com.sih.nivara.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link Person} records in a patient's personal memory.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
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

    /** Inserts a new person or updates an existing one. */
    @Transactional
    public Person save(Person person) {
        return personRepository.save(person);
    }
}
