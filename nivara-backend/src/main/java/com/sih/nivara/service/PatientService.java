package com.sih.nivara.service;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.RelationshipType;
import com.sih.nivara.entity.enums.UserRole;
import com.sih.nivara.repository.PatientRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link Patient} records.
 * Repository delegation, plus {@link #createWithOwner}, which creates a patient together with its
 * first caregiver link so that no patient ever exists without an OWNER, and
 * {@link #ensureLoginAccount}, which gives a patient their own PATIENT account.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientCaregiverService patientCaregiverService;
    private final UserService userService;

    @PersistenceContext
    private EntityManager entityManager;

    public PatientService(PatientRepository patientRepository,
                          PatientCaregiverService patientCaregiverService,
                          UserService userService) {
        this.patientRepository = patientRepository;
        this.patientCaregiverService = patientCaregiverService;
        this.userService = userService;
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
     * The patient whose own login this account is, or empty when the account is not a patient's.
     * Only a PATIENT account ever resolves, so no other account can act as a patient.
     */
    public Optional<Patient> findByUserAccount(AppUser account) {
        if (account == null || account.getRole() != UserRole.PATIENT) {
            return Optional.empty();
        }
        return patientRepository.findByUserAccount(account);
    }

    /**
     * The patient's own PATIENT account, created on first use. The patient row is locked and
     * re-read first, so pairing two devices at once still produces exactly one account: the second
     * waits for the first to commit and then sees its account.
     */
    @Transactional
    public AppUser ensureLoginAccount(Patient patient) {
        Patient locked = entityManager.contains(patient) ? patient : entityManager.find(Patient.class, patient.getId());
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        if (locked.getUserAccount() != null) {
            return locked.getUserAccount();
        }
        AppUser account = userService.save(
                AppUser.patientAccount(locked.getFullName(), locked.getPreferredLanguage()));
        locked.setUserAccount(account);
        return account;
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
