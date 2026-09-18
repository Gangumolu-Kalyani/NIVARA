package com.sih.nivara.service;

import com.sih.nivara.entity.PatientCaregiver;
import com.sih.nivara.repository.PatientCaregiverRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link PatientCaregiver} links between a patient and a caregiver.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 */
@Service
@Transactional(readOnly = true)
public class PatientCaregiverService {

    private final PatientCaregiverRepository patientCaregiverRepository;

    public PatientCaregiverService(PatientCaregiverRepository patientCaregiverRepository) {
        this.patientCaregiverRepository = patientCaregiverRepository;
    }

    /** All patient-caregiver links, unfiltered. */
    public List<PatientCaregiver> findAll() {
        return patientCaregiverRepository.findAll();
    }

    /** The patient-caregiver link with this id, or empty when none exists. */
    public Optional<PatientCaregiver> findById(Long id) {
        return patientCaregiverRepository.findById(id);
    }

    /** Inserts a new patient-caregiver link or updates an existing one. */
    @Transactional
    public PatientCaregiver save(PatientCaregiver patientCaregiver) {
        return patientCaregiverRepository.save(patientCaregiver);
    }

    /** Removes the patient-caregiver link with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        patientCaregiverRepository.deleteById(id);
    }
}
