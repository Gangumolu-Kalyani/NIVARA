package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link Patient} records (table patients).
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
}
