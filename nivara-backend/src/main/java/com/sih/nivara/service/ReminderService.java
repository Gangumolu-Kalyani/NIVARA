package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.ReminderMapper;
import com.sih.nivara.dto.request.ReminderCreateRequest;
import com.sih.nivara.dto.request.ReminderResponseRequest;
import com.sih.nivara.dto.request.ReminderUpdateRequest;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.ReminderOccurrence;
import com.sih.nivara.entity.enums.DayOfWeekCode;
import com.sih.nivara.entity.enums.PatientResponseType;
import com.sih.nivara.entity.enums.ReminderRepeatType;
import com.sih.nivara.entity.enums.ReminderResponseStatus;
import com.sih.nivara.repository.ReminderOccurrenceRepository;
import com.sih.nivara.repository.ReminderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Daily Assistance reminders and their occurrences.
 *
 * <p>Occurrences are created for the patient's current day, from each live reminder whose repeat
 * rule includes it: by the scheduler every minute, and on demand whenever today's data is read, so
 * a new reminder shows up at once. Creation is idempotent (see
 * {@link ReminderOccurrenceRepository#insertIfAbsent}).
 *
 * <p>Once an occurrence falls due, {@link #advance} moves it along: the patient is nudged, nudged
 * again every nudge interval while no answer comes, and after escalateAfterMissed unanswered
 * nudges the occurrence is ESCALATED and the care team alerted. An occurrence still unanswered when
 * the patient's day ends becomes MISSED. There is no patient device channel yet, so a "nudge" is
 * the record that the patient should have been prompted at that moment; a patient app would read
 * it from the daily-care endpoint.
 */
@Service
@Transactional(readOnly = true)
public class ReminderService {

    static final Set<ReminderResponseStatus> AWAITING = EnumSet.of(
            ReminderResponseStatus.PENDING, ReminderResponseStatus.SENT, ReminderResponseStatus.SEEN);

    private final ReminderRepository reminderRepository;
    private final ReminderOccurrenceRepository occurrenceRepository;
    private final AlertService alertService;
    private final Duration nudgeInterval;

    public ReminderService(ReminderRepository reminderRepository,
                           ReminderOccurrenceRepository occurrenceRepository,
                           AlertService alertService,
                           @Value("${nivara.reminders.nudge-interval:PT15M}") Duration nudgeInterval) {
        this.reminderRepository = reminderRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.alertService = alertService;
        this.nudgeInterval = nudgeInterval;
    }

    // ---- reminders ---------------------------------------------------------------------------

    /** A reminder by uuid, unless it was deleted. */
    public Optional<Reminder> findByUuid(UUID uuid) {
        return reminderRepository.findByUuidAndDeletedAtIsNull(uuid);
    }

    /** One patient's reminders, by time of day. */
    public List<Reminder> findByPatient(Patient patient) {
        return reminderRepository.findByPatientAndDeletedAtIsNullOrderByScheduledTimeAscTitleAscIdAsc(patient);
    }

    @Transactional
    public Reminder create(Patient patient, ReminderCreateRequest request, AppUser createdBy) {
        ReminderRepeatType repeatType = request.repeatType() != null ? request.repeatType() : ReminderRepeatType.DAILY;
        checkRepeatRule(repeatType, request.repeatDays(), request.oneOffDate());

        Reminder reminder = reminderRepository.save(ReminderMapper.toEntity(request, patient, createdBy));
        ensureOccurrences(patient, ReminderSchedule.localDate(patient, Instant.now()));
        return reminder;
    }

    /**
     * Replaces a reminder. When its schedule changes, or it is switched back on, the new schedule
     * takes effect from now: it does not produce an already-overdue occurrence for earlier today.
     * Future occurrences nothing has happened to yet are dropped and recreated from the new
     * schedule; any occurrence with history is kept.
     */
    @Transactional
    public Reminder update(UUID uuid, ReminderUpdateRequest request) {
        checkRepeatRule(request.repeatType(), request.repeatDays(), request.oneOffDate());
        Reminder reminder = requireManaged(uuid);

        boolean wasLive = reminder.isLive();
        ScheduleKey before = ScheduleKey.of(reminder);
        ReminderMapper.applyUpdate(request, reminder);

        Instant now = Instant.now();
        if (!before.equals(ScheduleKey.of(reminder)) || (!wasLive && reminder.isLive())) {
            reminder.setEffectiveFrom(now);
        }
        reminderRepository.flush();
        occurrenceRepository.deleteUntouchedFuture(reminder, now);
        ensureOccurrences(reminder.getPatient(), ReminderSchedule.localDate(reminder.getPatient(), now));
        return reminder;
    }

    /**
     * Deletes a reminder. It is kept, deactivated, for its occurrence history, and disappears from
     * the API; its untouched future occurrences are removed.
     */
    @Transactional
    public void delete(UUID uuid) {
        Reminder reminder = requireManaged(uuid);
        Instant now = Instant.now();
        reminder.setActive(false);
        reminder.setDeletedAt(now);
        reminderRepository.flush();
        occurrenceRepository.deleteUntouchedFuture(reminder, now);
    }

    // ---- occurrences -------------------------------------------------------------------------

    /**
     * Makes sure every live reminder of the patient that falls on this date has its occurrence.
     * Only meant for the patient's current day: past days are history, and future days would go
     * stale if a reminder changed.
     */
    @Transactional
    public void ensureOccurrences(Patient patient, LocalDate date) {
        for (Reminder reminder : reminderRepository.findByPatientAndActiveTrueAndDeletedAtIsNull(patient)) {
            if (ReminderSchedule.isDueOn(reminder, date)) {
                occurrenceRepository.insertIfAbsent(UUID.randomUUID(), reminder.getId(), patient.getId(),
                        ReminderSchedule.scheduledAt(reminder, date), date);
            }
        }
    }

    /**
     * The patient's reminders for one of their days, in time order. For today, missing occurrences
     * are created first, so a reminder added a minute ago is already listed.
     */
    @Transactional
    public List<ReminderOccurrence> dailyCare(Patient patient, LocalDate date) {
        if (date.equals(ReminderSchedule.localDate(patient, Instant.now()))) {
            ensureOccurrences(patient, date);
        }
        return occurrenceRepository.findByPatientAndLocalDateOrderByScheduledAtAscIdAsc(patient, date);
    }

    /** The patient's occurrences over a range of their days, inclusive, in time order. */
    public List<ReminderOccurrence> occurrencesBetween(Patient patient, LocalDate from, LocalDate to) {
        return occurrenceRepository.findByPatientAndLocalDateBetweenOrderByScheduledAtAscIdAsc(patient, from, to);
    }

    /** A reminder's occurrences, most recent first. */
    public List<ReminderOccurrence> history(Reminder reminder) {
        return occurrenceRepository.findByReminderOrderByScheduledAtDescIdDesc(reminder);
    }

    /**
     * Records the patient's answer to one occurrence of a reminder.
     *
     * <ul>
     *   <li>TAKEN completes it, even after it was escalated or missed.</li>
     *   <li>REMIND_LATER marks it SEEN and restarts the nudge interval. It does not reset the nudge
     *       count, so postponing forever still ends in an escalation.</li>
     *   <li>NEED_HELP escalates it at once and raises a HIGH alert.</li>
     * </ul>
     *
     * 404 when the reminder has no such occurrence, 409 when it is already completed, or when
     * REMIND_LATER is sent for an occurrence no longer waiting for an answer.
     */
    @Transactional
    public ReminderOccurrence respond(Reminder reminder, ReminderResponseRequest request) {
        Instant now = Instant.now();
        Patient patient = reminder.getPatient();
        ensureOccurrences(patient, ReminderSchedule.localDate(patient, now));

        ReminderOccurrence occurrence = (request.scheduledAt() != null
                ? occurrenceRepository.findByReminderAndScheduledAt(reminder, request.scheduledAt())
                : occurrenceRepository.findFirstByReminderAndScheduledAtLessThanEqualOrderByScheduledAtDesc(reminder, now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        request.scheduledAt() != null
                                ? "This reminder has no occurrence at " + request.scheduledAt()
                                : "This reminder has not fallen due yet"));

        if (occurrence.getResponseStatus() == ReminderResponseStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This reminder was already completed");
        }

        PatientResponseType type = request.responseType();
        switch (type) {
            case TAKEN -> {
                occurrence.respond(type, now);
                occurrence.setResponseStatus(ReminderResponseStatus.COMPLETED);
            }
            case REMIND_LATER -> {
                if (!occurrence.isAwaitingResponse()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "This reminder is " + occurrence.getResponseStatus().name().toLowerCase()
                                    + " and can no longer be postponed");
                }
                occurrence.respond(type, now);
                occurrence.setResponseStatus(ReminderResponseStatus.SEEN);
                occurrence.setNotifiedAt(now);
            }
            case NEED_HELP -> {
                occurrence.respond(type, now);
                if (!occurrence.isEscalated()) {
                    occurrence.escalate(now);
                }
                alertService.raiseForOccurrence(occurrence, AlertService.EscalationReason.NEED_HELP);
            }
        }
        return occurrence;
    }

    /**
     * Moves one due occurrence along its lifecycle at this instant; see the class comment. Does
     * nothing if it is no longer waiting for an answer. Called by {@link ReminderScheduler}, one
     * occurrence per transaction.
     */
    @Transactional
    public void advance(Long occurrenceId, Instant now) {
        ReminderOccurrence occurrence = occurrenceRepository.findById(occurrenceId).orElse(null);
        if (occurrence == null || !occurrence.isAwaitingResponse() || occurrence.getScheduledAt().isAfter(now)) {
            return;
        }

        Patient patient = occurrence.getPatient();
        if (occurrence.getLocalDate().isBefore(ReminderSchedule.localDate(patient, now))) {
            occurrence.setResponseStatus(ReminderResponseStatus.MISSED);
            return;
        }

        Reminder reminder = occurrence.getReminder();
        if (!reminder.isLive()) {
            // Switched off after it fell due: stop prompting, and let the day end mark it missed.
            return;
        }

        if (occurrence.getNotifiedAt() == null) {
            occurrence.nudge(now);
        } else if (!now.isBefore(occurrence.getNotifiedAt().plus(nudgeInterval))) {
            if (occurrence.getNudgeCount() >= reminder.getEscalateAfterMissed()) {
                occurrence.escalate(now);
                alertService.raiseForOccurrence(occurrence, AlertService.EscalationReason.NO_RESPONSE);
            } else {
                occurrence.nudge(now);
            }
        }
    }

    /** Ids of every occurrence that has fallen due and is still waiting for an answer. */
    public List<Long> dueAwaitingOccurrenceIds(Instant now) {
        return occurrenceRepository.findByResponseStatusInAndScheduledAtLessThanEqual(AWAITING, now).stream()
                .map(ReminderOccurrence::getId)
                .toList();
    }

    /** Every patient with at least one live reminder. */
    public List<Patient> patientsWithLiveReminders() {
        return reminderRepository.findPatientsWithLiveReminders();
    }

    /**
     * Reloads a reminder inside the current transaction, so changes to it are saved. The caller
     * has already found it and checked access, so a miss here means it was deleted meanwhile.
     */
    private Reminder requireManaged(UUID uuid) {
        return reminderRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No reminder with uuid " + uuid));
    }

    /**
     * WEEKLY needs at least one day and ONCE needs a date; the repeat fields that do not apply to
     * the type are ignored by the mapper, so only missing ones are an error.
     */
    private static void checkRepeatRule(ReminderRepeatType repeatType, Set<DayOfWeekCode> repeatDays,
                                        LocalDate oneOffDate) {
        if (repeatType == ReminderRepeatType.WEEKLY && (repeatDays == null || repeatDays.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A WEEKLY reminder needs at least one day in repeatDays");
        }
        if (repeatType == ReminderRepeatType.ONCE && oneOffDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A ONCE reminder needs oneOffDate");
        }
    }

    /** The fields that decide when a reminder falls due. */
    private record ScheduleKey(LocalTime time, ReminderRepeatType type, Set<DayOfWeekCode> days, LocalDate date) {
        static ScheduleKey of(Reminder reminder) {
            return new ScheduleKey(reminder.getScheduledTime(), reminder.getRepeatType(),
                    Set.copyOf(reminder.getRepeatDays()), reminder.getOneOffDate());
        }
    }
}
