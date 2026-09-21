package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PersonalObject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    /**
     * Looks a record up by the public identifier the API uses, unscoped. Backed by the
     * unique constraint uq_personal_objects_uuid (V2). Fetches the patient because the API response
     * carries its uuid and open-in-view is disabled.
     */
    @EntityGraph(attributePaths = "patient")
    Optional<PersonalObject> findByUuid(UUID uuid);

    /** One patient's objects, by name as stored, with the id breaking ties. */
    @EntityGraph(attributePaths = "patient")
    List<PersonalObject> findByPatientOrderByNameAscIdAsc(Patient patient);
}
