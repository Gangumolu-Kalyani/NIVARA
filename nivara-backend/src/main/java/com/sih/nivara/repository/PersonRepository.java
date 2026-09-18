package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link Person} records in a patient's personal memory (table people).
 */
@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {

    /**
     * Looks one of this patient's records up by its public uuid. Scoping the query to the
     * patient is what keeps one patient's data from being referenced by another's: the
     * database has no composite foreign key that could enforce it.
     */
    Optional<Person> findByUuidAndPatient(UUID uuid, Patient patient);
}
