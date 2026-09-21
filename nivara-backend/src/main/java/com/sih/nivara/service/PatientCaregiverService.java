package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.PatientCaregiverMapper;
import com.sih.nivara.dto.request.CaregiverAccessUpdateRequest;
import com.sih.nivara.dto.request.CaregiverAssignmentRequest;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PatientCaregiver;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.RelationshipType;
import com.sih.nivara.repository.PatientCaregiverRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link PatientCaregiver} links between a patient and a caregiver: the table
 * every authorization decision reads, and the rules for changing it.
 *
 * <p>Two invariants hold for every patient: at least one OWNER, and exactly one primary caregiver.
 * The creator becomes both when the patient is created. Afterwards the last OWNER cannot be
 * demoted or removed, and the primary role can only be moved to another caregiver, never cleared
 * or deleted with its holder. Who may call these methods is decided by the caller, through
 * {@link PatientAccessService}; nothing here checks the acting account.
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

    /** This account's link to this patient, or empty when it has none. */
    public Optional<PatientCaregiver> findLink(Patient patient, AppUser caregiverUser) {
        return patientCaregiverRepository.findByPatientAndCaregiverUser(patient, caregiverUser);
    }

    /** Every link this account holds, with the patient fetched, by patient name. */
    public List<PatientCaregiver> findByCaregiver(AppUser caregiverUser) {
        return patientCaregiverRepository.findByCaregiverUserOrderByPatientFullNameAscIdAsc(caregiverUser);
    }

    /** The care team of one patient, with both sides of each link fetched. */
    public List<PatientCaregiver> findByPatient(Patient patient) {
        return patientCaregiverRepository.findByPatientOrderByIdAsc(patient);
    }

    /** Makes the creator of a new patient its OWNER and primary caregiver. */
    @Transactional
    public PatientCaregiver createOwnerLink(Patient patient, AppUser creator, RelationshipType relationship) {
        PatientCaregiver link = new PatientCaregiver(patient, creator, relationship);
        link.setAccessLevel(AccessLevel.OWNER);
        link.setPrimary(true);
        link.setReceivesAlerts(true);
        return patientCaregiverRepository.save(link);
    }

    /**
     * Gives an account access to this patient. 409 when it already has access, or when the request
     * asks for primary while the patient already has a primary caregiver.
     */
    @Transactional
    public PatientCaregiver grant(Patient patient, AppUser grantee, CaregiverAssignmentRequest request) {
        if (patientCaregiverRepository.findByPatientAndCaregiverUser(patient, grantee).isPresent()) {
            throw conflict("This account already has access to the patient");
        }
        if (Boolean.TRUE.equals(request.primary())
                && patientCaregiverRepository.findByPatientAndPrimaryTrue(patient).isPresent()) {
            throw conflict("The patient already has a primary caregiver");
        }
        try {
            return patientCaregiverRepository.saveAndFlush(PatientCaregiverMapper.toEntity(request, patient, grantee));
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent grants can both pass the checks above; the database decides.
            throw translateUniqueViolation(ex);
        }
    }

    /**
     * Replaces the access level, relationship, primary flag and alert setting of one caregiver's
     * link. 404 when that caregiver has no link to the patient; 409 when the change would leave the
     * patient without an OWNER or without a primary caregiver.
     */
    @Transactional
    public PatientCaregiver update(Patient patient, UUID caregiverUuid, CaregiverAccessUpdateRequest request) {
        PatientCaregiver link = requireLink(patient, caregiverUuid);

        if (link.getAccessLevel() == AccessLevel.OWNER && request.accessLevel() != AccessLevel.OWNER
                && isLastOwner(patient)) {
            throw conflict("The last OWNER of a patient cannot be demoted");
        }
        if (link.isPrimary() && !request.primary()) {
            throw conflict("A patient keeps one primary caregiver; make another caregiver primary instead");
        }
        if (!link.isPrimary() && request.primary()) {
            // Move the primary role. The old holder is cleared and flushed first, because the
            // partial unique index uq_patient_caregivers_one_primary is checked row by row.
            patientCaregiverRepository.findByPatientAndPrimaryTrue(patient).ifPresent(current -> {
                current.setPrimary(false);
                patientCaregiverRepository.flush();
            });
        }

        link.setAccessLevel(request.accessLevel());
        link.setRelationship(request.relationship());
        link.setPrimary(request.primary());
        link.setReceivesAlerts(request.receivesAlerts());
        try {
            patientCaregiverRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw translateUniqueViolation(ex);
        }
        return link;
    }

    /**
     * Removes one caregiver's access to this patient. 404 when that caregiver has no link; 409 for
     * the last OWNER, or for the primary caregiver until the primary role has been moved.
     */
    @Transactional
    public void revoke(Patient patient, UUID caregiverUuid) {
        PatientCaregiver link = requireLink(patient, caregiverUuid);
        if (link.getAccessLevel() == AccessLevel.OWNER && isLastOwner(patient)) {
            throw conflict("The last OWNER of a patient cannot be removed");
        }
        if (link.isPrimary()) {
            throw conflict("The primary caregiver cannot be removed; make another caregiver primary first");
        }
        patientCaregiverRepository.delete(link);
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

    private PatientCaregiver requireLink(Patient patient, UUID caregiverUuid) {
        return patientCaregiverRepository.findByPatientAndCaregiverUserUuid(patient, caregiverUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No caregiver " + caregiverUuid + " for this patient"));
    }

    private boolean isLastOwner(Patient patient) {
        return patientCaregiverRepository.countByPatientAndAccessLevel(patient, AccessLevel.OWNER) <= 1;
    }

    private static RuntimeException translateUniqueViolation(DataIntegrityViolationException ex) {
        String cause = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (cause.contains("uq_patient_caregivers_patient_caregiver")) {
            return conflict("This account already has access to the patient");
        }
        if (cause.contains("uq_patient_caregivers_one_primary")) {
            return conflict("The patient already has a primary caregiver");
        }
        return ex;
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
