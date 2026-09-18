package com.sih.nivara.service;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link Patient} records.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;

    public PatientService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    /** All patients, unfiltered. */
    public List<Patient> findAll() {
        return patientRepository.findAll();
    }

    /** The patient with this id, or empty when none exists. */
    public Optional<Patient> findById(Long id) {
        return patientRepository.findById(id);
    }

    /** The patient with this public uuid, or empty when none exists. */
    public Optional<Patient> findByUuid(UUID uuid) {
        return patientRepository.findByUuid(uuid);
    }

    /** Inserts a new patient or updates an existing one. */
    @Transactional
    public Patient save(Patient patient) {
        return patientRepository.save(patient);
    }
}
