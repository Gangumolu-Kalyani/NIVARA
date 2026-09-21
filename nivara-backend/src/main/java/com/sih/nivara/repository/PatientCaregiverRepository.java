package com.sih.nivara.repository;

import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PatientCaregiver;
import com.sih.nivara.entity.enums.AccessLevel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link PatientCaregiver} links between a patient and a caregiver (table patient_caregivers).
 *
 * <p>This table is the authorization boundary: every access decision reads it.
 */
@Repository
public interface PatientCaregiverRepository extends JpaRepository<PatientCaregiver, Long> {

    /**
     * The link between this patient and this account, if any. Backed by the unique constraint
     * uq_patient_caregivers_patient_caregiver, so it matches at most one row.
     */
    Optional<PatientCaregiver> findByPatientAndCaregiverUser(Patient patient, AppUser caregiverUser);

    /**
     * Every patient this account can reach, by patient name, with the patient fetched. Uses index
     * ix_patient_caregivers_caregiver_user_id.
     */
    @EntityGraph(attributePaths = "patient")
    List<PatientCaregiver> findByCaregiverUserOrderByPatientFullNameAscIdAsc(AppUser caregiverUser);

    /** The care team of one patient, in the order access was granted, with both sides fetched. */
    @EntityGraph(attributePaths = {"patient", "caregiverUser"})
    List<PatientCaregiver> findByPatientOrderByIdAsc(Patient patient);

    /** One caregiver's link to this patient, named by the caregiver's public uuid. */
    @EntityGraph(attributePaths = {"patient", "caregiverUser"})
    Optional<PatientCaregiver> findByPatientAndCaregiverUserUuid(Patient patient, UUID caregiverUuid);

    /** The patient's primary caregiver; uq_patient_caregivers_one_primary allows at most one. */
    Optional<PatientCaregiver> findByPatientAndPrimaryTrue(Patient patient);

    /** How many caregivers hold this level for the patient; used to protect the last OWNER. */
    long countByPatientAndAccessLevel(Patient patient, AccessLevel accessLevel);
}
