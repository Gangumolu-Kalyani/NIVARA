package com.sih.nivara.device.repository;

import com.sih.nivara.device.entity.PatientDevice;
import com.sih.nivara.entity.Patient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to {@link PatientDevice} records (table patient_devices). The finders fetch the patient
 * and the patient's login account, which every caller of them reads.
 */
@Repository
public interface PatientDeviceRepository extends JpaRepository<PatientDevice, Long> {

    @EntityGraph(attributePaths = {"patient", "patient.userAccount"})
    Optional<PatientDevice> findByUuid(UUID uuid);

    /** The device waiting to be paired with this code. Backed by uq_patient_devices_pairing_code. */
    @EntityGraph(attributePaths = {"patient", "patient.userAccount"})
    Optional<PatientDevice> findByPairingCodeHash(String pairingCodeHash);

    /** One patient's devices, newest first. */
    @EntityGraph(attributePaths = {"patient", "createdByUser"})
    List<PatientDevice> findByPatientOrderByCreatedAtDescIdDesc(Patient patient);
}
