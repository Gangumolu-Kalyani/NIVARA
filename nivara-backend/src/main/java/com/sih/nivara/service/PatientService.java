package com.sih.nivara.service;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.RelationshipType;
import com.sih.nivara.repository.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link Patient} records.
 * Repository delegation, plus {@link #createWithOwner}, which creates a patient together with its
 * first caregiver link so that no patient ever exists without an OWNER.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientCaregiverService patientCaregiverService;

    public PatientService(PatientRepository patientRepository, PatientCaregiverService patientCaregiverService) {
        this.patientRepository = patientRepository;
        this.patientCaregiverService = patientCaregiverService;
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

    /**
     * Creates a patient and makes its creator the OWNER and primary caregiver, in one transaction:
     * if the caregiver link cannot be written, the patient is not created either.
     */
    @Transactional
    public Patient createWithOwner(Patient patient, AppUser creator, RelationshipType relationship) {
        Patient saved = patientRepository.save(patient);
        patientCaregiverService.createOwnerLink(saved, creator, relationship);
        return saved;
    }

    /** Inserts a new patient or updates an existing one. */
    @Transactional
    public Patient save(Patient patient) {
        return patientRepository.save(patient);
    }
}
