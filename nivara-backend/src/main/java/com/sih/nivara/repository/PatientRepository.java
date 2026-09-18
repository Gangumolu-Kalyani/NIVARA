package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link Patient} records (table patients).
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {

    /**
     * Looks a patient up by the public identifier the API uses. Backed by the unique
     * constraint uq_patients_uuid (V1), so it matches at most one row.
     */
    Optional<Patient> findByUuid(UUID uuid);
}
