package com.sih.nivara.service;

import com.sih.nivara.entity.Alert;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.ReminderOccurrence;
import com.sih.nivara.entity.enums.AlertCategory;
import com.sih.nivara.entity.enums.AlertSeverity;
import com.sih.nivara.entity.enums.AlertStatus;
import com.sih.nivara.entity.enums.AlertType;
import com.sih.nivara.entity.enums.ReminderCategory;
import com.sih.nivara.repository.AlertRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raises, lists and closes {@link Alert}s.
 *
 * <p>Alerts are raised by the system, never through the API: when a reminder goes unanswered
 * after its allowed nudges, or when the patient asks for help. Caregivers only read and close
 * them. Nothing is pushed to caregivers' devices yet; the alert center and dashboard show them.
 */
@Service
@Transactional(readOnly = true)
public class AlertService {

    /** Why a reminder occurrence was escalated to the care team. */
    public enum EscalationReason {
        NO_RESPONSE,
        NEED_HELP
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public Optional<Alert> findByUuid(UUID uuid) {
        return alertRepository.findByUuid(uuid);
    }

    /** One patient's alerts, newest first, optionally only those with this status. */
    public List<Alert> findByPatient(Patient patient, AlertStatus status) {
        return status == null
                ? alertRepository.findByPatientOrderByCreatedAtDescIdDesc(patient)
                : alertRepository.findByPatientAndStatusOrderByCreatedAtDescIdDesc(patient, status);
    }

    /** Raises an alert for a reminder occurrence the care team should look at. */
    @Transactional
    public Alert raiseForOccurrence(ReminderOccurrence occurrence, EscalationReason reason) {
        Reminder reminder = occurrence.getReminder();
        Patient patient = occurrence.getPatient();
        String who = patient.getPreferredName() != null ? patient.getPreferredName() : patient.getFullName();
        String at = ReminderSchedule.localTime(patient, occurrence.getScheduledAt()).format(TIME);

        String title;
        String message;
        AlertSeverity severity;
        if (reason == EscalationReason.NEED_HELP) {
            title = "Help requested: " + reminder.getTitle();
            message = who + " asked for help with \"" + reminder.getTitle() + "\" (scheduled " + at + ").";
            severity = AlertSeverity.HIGH;
        } else {
            title = "No response: " + reminder.getTitle();
            message = who + " has not responded to \"" + reminder.getTitle() + "\" (scheduled " + at
                    + ") after " + occurrence.getNudgeCount() + " reminder"
                    + (occurrence.getNudgeCount() == 1 ? "" : "s") + ".";
            severity = severityOf(reminder.getCategory());
        }

        return alertRepository.save(new Alert(patient, occurrence, AlertType.REMINDER_ESCALATION,
                AlertCategory.valueOf(reminder.getCategory().name()), severity, title, message));
    }

    /**
     * Closes an open alert as RESOLVED or DISMISSED. 409 if it is already closed, so two
     * caregivers acting at once cannot both close it.
     */
    @Transactional
    public Alert close(UUID uuid, AlertStatus closedAs, AppUser closedBy) {
        Alert alert = alertRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert with uuid " + uuid));
        if (alert.getStatus() != AlertStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This alert is already " + alert.getStatus().name().toLowerCase());
        }
        alert.close(closedAs, closedBy, Instant.now());
        return alert;
    }

    /** How urgent an unanswered reminder is, by what it was for. */
    static AlertSeverity severityOf(ReminderCategory category) {
        return switch (category) {
            case MEDICINE, APPOINTMENT -> AlertSeverity.HIGH;
            case MEAL, HYDRATION -> AlertSeverity.MEDIUM;
            case MOVEMENT, COGNITIVE_ACTIVITY -> AlertSeverity.LOW;
        };
    }
}
