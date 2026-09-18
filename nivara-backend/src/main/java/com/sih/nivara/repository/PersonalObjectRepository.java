package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PersonalObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link PersonalObject} records in a patient's personal memory (table personal_objects).
 */
@Repository
public interface PersonalObjectRepository extends JpaRepository<PersonalObject, Long> {

    /**
     * Looks one of this patient's records up by its public uuid. Scoping the query to the
     * patient is what keeps one patient's data from being referenced by another's: the
     * database has no composite foreign key that could enforce it.
     */
    Optional<PersonalObject> findByUuidAndPatient(UUID uuid, Patient patient);
}
