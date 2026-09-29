package com.sih.nivara.repository;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.ReminderOccurrence;
import com.sih.nivara.entity.enums.ReminderResponseStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Access to {@link ReminderOccurrence} records (table reminder_occurrences).
 *
 * <p>Finders fetch the reminder with each occurrence, because every response names its reminder
 * and open-in-view is disabled.
 */
@Repository
public interface ReminderOccurrenceRepository extends JpaRepository<ReminderOccurrence, Long> {

    /**
     * Creates the occurrence for one reminder slot unless it already exists. The unique constraint
     * uq_reminder_occurrences_slot makes this safe when the scheduler and a request create the
     * same day at once. Answers the number of rows inserted: 0 or 1.
     */
    @Modifying
    @Query(value = """
            INSERT INTO reminder_occurrences (uuid, reminder_id, patient_id, scheduled_at, local_date)
            VALUES (:uuid, :reminderId, :patientId, :scheduledAt, :localDate)
            ON CONFLICT (reminder_id, scheduled_at) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("uuid") UUID uuid,
                       @Param("reminderId") Long reminderId,
                       @Param("patientId") Long patientId,
                       @Param("scheduledAt") Instant scheduledAt,
                       @Param("localDate") LocalDate localDate);

    /** One patient's occurrences on one of their calendar days, in time order. */
    @EntityGraph(attributePaths = {"reminder", "patient"})
    List<ReminderOccurrence> findByPatientAndLocalDateOrderByScheduledAtAscIdAsc(Patient patient, LocalDate localDate);

    /** One patient's occurrences over a range of their calendar days, inclusive. */
    @EntityGraph(attributePaths = {"reminder", "patient"})
    List<ReminderOccurrence> findByPatientAndLocalDateBetweenOrderByScheduledAtAscIdAsc(Patient patient,
                                                                                       LocalDate from,
                                                                                       LocalDate to);

    /** A reminder's history, most recent first. */
    @EntityGraph(attributePaths = {"reminder", "patient"})
    List<ReminderOccurrence> findByReminderOrderByScheduledAtDescIdDesc(Reminder reminder);

    @EntityGraph(attributePaths = {"reminder", "patient"})
    Optional<ReminderOccurrence> findByReminderAndScheduledAt(Reminder reminder, Instant scheduledAt);

    /** The reminder's latest occurrence that has already fallen due. */
    @EntityGraph(attributePaths = {"reminder", "patient"})
    Optional<ReminderOccurrence> findFirstByReminderAndScheduledAtLessThanEqualOrderByScheduledAtDesc(
            Reminder reminder, Instant now);

    /** Occurrences that are due and still waiting for an answer, for the scheduler. */
    @EntityGraph(attributePaths = {"reminder", "patient"})
    List<ReminderOccurrence> findByResponseStatusInAndScheduledAtLessThanEqual(
            Collection<ReminderResponseStatus> statuses, Instant now);

    /**
     * Removes a reminder's future occurrences that nothing has happened to yet, so a rescheduled or
     * disabled reminder does not leave stale slots behind. Occurrences with any history are kept.
     */
    @Modifying
    @Query("""
            delete from ReminderOccurrence o
            where o.reminder = :reminder
              and o.scheduledAt > :now
              and o.responseStatus = com.sih.nivara.entity.enums.ReminderResponseStatus.PENDING
              and o.nudgeCount = 0
              and o.responseType is null
              and not exists (select a from Alert a where a.reminderOccurrence = o)
            """)
    int deleteUntouchedFuture(@Param("reminder") Reminder reminder, @Param("now") Instant now);
}
