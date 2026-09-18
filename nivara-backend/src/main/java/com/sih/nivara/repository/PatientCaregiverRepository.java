package com.sih.nivara.repository;

import com.sih.nivara.entity.PatientCaregiver;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link PatientCaregiver} links between a patient and a caregiver (table patient_caregivers).
 */
@Repository
public interface PatientCaregiverRepository extends JpaRepository<PatientCaregiver, Long> {
}
