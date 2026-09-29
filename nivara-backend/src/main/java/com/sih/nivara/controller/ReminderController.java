package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.ReminderMapper;
import com.sih.nivara.dto.request.ReminderCreateRequest;
import com.sih.nivara.dto.request.ReminderResponseRequest;
import com.sih.nivara.dto.request.ReminderUpdateRequest;
import com.sih.nivara.dto.response.ReminderOccurrenceResponse;
import com.sih.nivara.dto.response.ReminderResponse;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Reminder;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.service.ReminderSchedule;
import com.sih.nivara.service.ReminderService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Daily Assistance REST API: reminders, the patient's answers to them, and the day's timeline.
 *
 * <p>Like the memory API, reminders are created and listed under their patient and then addressed
 * by their own uuid. Reading needs VIEWER and writing needs EDITOR access to the patient.
 *
 * <p>Answers are recorded through {@code POST /api/reminders/{uuid}/responses}. Patients do not
 * have their own logins yet, so for now a caregiver with EDITOR access records the answer on the
 * patient's behalf.
 */
@RestController
public class ReminderController {

    private final ReminderService reminderService;
    private final PatientAccessService patientAccessService;

    public ReminderController(ReminderService reminderService, PatientAccessService patientAccessService) {
        this.reminderService = reminderService;
        this.patientAccessService = patientAccessService;
    }

    /** Adds a reminder. 201 with its Location; 400 when a WEEKLY has no days or a ONCE no date. */
    @PostMapping("/api/patients/{patientUuid}/reminders")
    public ResponseEntity<ReminderResponse> create(@PathVariable UUID patientUuid,
                                                   @Valid @RequestBody ReminderCreateRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);
        Reminder saved = reminderService.create(patient, request, patientAccessService.requireCaller());
        ReminderResponse body = ReminderMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/reminders/" + body.uuid())).body(body);
    }

    /** This patient's reminders, active and paused, by time of day. */
    @GetMapping("/api/patients/{patientUuid}/reminders")
    public List<ReminderResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return reminderService.findByPatient(patient).stream().map(ReminderMapper::toResponse).toList();
    }

    @GetMapping("/api/reminders/{uuid}")
    public ReminderResponse findByUuid(@PathVariable UUID uuid) {
        return ReminderMapper.toResponse(requireReminder(uuid, AccessLevel.VIEWER));
    }

    /** Replaces a reminder, including switching it on or off through active. */
    @PutMapping("/api/reminders/{uuid}")
    public ReminderResponse update(@PathVariable UUID uuid, @Valid @RequestBody ReminderUpdateRequest request) {
        requireReminder(uuid, AccessLevel.EDITOR);
        return ReminderMapper.toResponse(reminderService.update(uuid, request));
    }

    /** Deletes a reminder. Its past occurrences stay in the patient's history. */
    @DeleteMapping("/api/reminders/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        requireReminder(uuid, AccessLevel.EDITOR);
        reminderService.delete(uuid);
        return ResponseEntity.noContent().build();
    }

    /**
     * Records the patient's answer to this reminder. 404 when the named occurrence does not exist
     * or, without scheduledAt, when the reminder has not fallen due yet; 409 when it was already
     * completed.
     */
    @PostMapping("/api/reminders/{uuid}/responses")
    public ReminderOccurrenceResponse respond(@PathVariable UUID uuid,
                                              @Valid @RequestBody ReminderResponseRequest request) {
        Reminder reminder = requireReminder(uuid, AccessLevel.EDITOR);
        return ReminderMapper.toOccurrenceResponse(reminderService.respond(reminder, request));
    }

    /** Every occurrence of this reminder, most recent first. */
    @GetMapping("/api/reminders/{uuid}/responses")
    public List<ReminderOccurrenceResponse> history(@PathVariable UUID uuid) {
        Reminder reminder = requireReminder(uuid, AccessLevel.VIEWER);
        return reminderService.history(reminder).stream().map(ReminderMapper::toOccurrenceResponse).toList();
    }

    /**
     * The patient's reminders for one of their days, in time order; today when date is omitted.
     * Only today and past days have occurrences: a future day answers an empty list.
     */
    @GetMapping("/api/patients/{patientUuid}/daily-care")
    public List<ReminderOccurrenceResponse> dailyCare(
            @PathVariable UUID patientUuid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        LocalDate day = date != null ? date : ReminderSchedule.localDate(patient, Instant.now());
        return reminderService.dailyCare(patient, day).stream().map(ReminderMapper::toOccurrenceResponse).toList();
    }

    /** Loads a live reminder and checks the caller's access to its patient; 404 either way. */
    private Reminder requireReminder(UUID uuid, AccessLevel minimum) {
        Reminder reminder = reminderService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(reminder.getPatient(), minimum, () -> notFound(uuid));
        return reminder;
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No reminder with uuid " + uuid);
    }
}
