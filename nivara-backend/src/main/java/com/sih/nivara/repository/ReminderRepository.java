package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link Reminder} records (table reminders). Deleted reminders are kept for their
 * occurrence history, so every finder the API uses excludes them.
 */
@Repository
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /** A reminder by its public uuid, unless deleted. Backed by uq_reminders_uuid (V5). */
    @EntityGraph(attributePaths = "patient")
    Optional<Reminder> findByUuidAndDeletedAtIsNull(UUID uuid);

    /** One patient's reminders, by time of day and then title. */
    @EntityGraph(attributePaths = "patient")
    List<Reminder> findByPatientAndDeletedAtIsNullOrderByScheduledTimeAscTitleAscIdAsc(Patient patient);

    /** One patient's reminders that can still fall due. */
    List<Reminder> findByPatientAndActiveTrueAndDeletedAtIsNull(Patient patient);

    /** Every patient that has at least one live reminder, for the scheduler. */
    @Query("select distinct r.patient from Reminder r where r.active = true and r.deletedAt is null")
    List<Patient> findPatientsWithLiveReminders();
}
