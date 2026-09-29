package com.sih.nivara.service;

import com.sih.nivara.entity.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Drives reminders forward in time. Every minute it creates each patient's occurrences for their
 * current day, then advances every due occurrence still waiting for an answer: nudge, escalate,
 * or mark missed (see {@link ReminderService}).
 *
 * <p>Each patient and each occurrence is handled in its own transaction, so one failure, such as
 * a caregiver answering at the same moment (an optimistic-lock conflict), only skips that item
 * until the next run. Runs in a single application instance; several instances would each nudge,
 * which the optimistic lock keeps consistent but would do redundant work.
 *
 * <p>Set nivara.reminders.scheduler.enabled=false to switch it off, for example in tests.
 */
@Component
@ConditionalOnProperty(name = "nivara.reminders.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${nivara.reminders.scheduler.delay:PT1M}")
    public void run() {
        Instant now = Instant.now();

        for (Patient patient : reminderService.patientsWithLiveReminders()) {
            try {
                reminderService.ensureOccurrences(patient, ReminderSchedule.localDate(patient, now));
            } catch (RuntimeException e) {
                log.warn("Could not create today's reminder occurrences for patient {}", patient.getUuid(), e);
            }
        }

        for (Long occurrenceId : reminderService.dueAwaitingOccurrenceIds(now)) {
            try {
                reminderService.advance(occurrenceId, now);
            } catch (OptimisticLockingFailureException e) {
                log.debug("Reminder occurrence {} changed concurrently; retrying next run", occurrenceId);
            } catch (RuntimeException e) {
                log.warn("Could not advance reminder occurrence {}", occurrenceId, e);
            }
        }
    }
}
