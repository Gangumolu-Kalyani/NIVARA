package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link Place} records in a patient's personal memory (table places).
 */
@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * Looks one of this patient's records up by its public uuid. Scoping the query to the
     * patient is what keeps one patient's data from being referenced by another's: the
     * database has no composite foreign key that could enforce it.
     */
    Optional<Place> findByUuidAndPatient(UUID uuid, Patient patient);
}
