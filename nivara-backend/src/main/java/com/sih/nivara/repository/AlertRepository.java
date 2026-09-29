package com.sih.nivara.repository;

import com.sih.nivara.entity.Alert;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AlertStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to {@link Alert} records (table alerts). Finders fetch what an alert response reads.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, Long> {

    @EntityGraph(attributePaths = {"patient", "reminderOccurrence", "resolvedByUser"})
    Optional<Alert> findByUuid(UUID uuid);

    /** One patient's alerts, newest first. Follows index ix_alerts_patient_created. */
    @EntityGraph(attributePaths = {"patient", "reminderOccurrence", "resolvedByUser"})
    List<Alert> findByPatientOrderByCreatedAtDescIdDesc(Patient patient);

    @EntityGraph(attributePaths = {"patient", "reminderOccurrence", "resolvedByUser"})
    List<Alert> findByPatientAndStatusOrderByCreatedAtDescIdDesc(Patient patient, AlertStatus status);
}
