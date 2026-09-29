package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.AlertMapper;
import com.sih.nivara.dto.response.AlertResponse;
import com.sih.nivara.entity.Alert;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.AlertStatus;
import com.sih.nivara.service.AlertService;
import com.sih.nivara.service.PatientAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Alert center REST API. Alerts are raised by the system, so there is no create endpoint:
 * caregivers read them (VIEWER) and resolve or dismiss them (EDITOR).
 */
@RestController
public class AlertController {

    private final AlertService alertService;
    private final PatientAccessService patientAccessService;

    public AlertController(AlertService alertService, PatientAccessService patientAccessService) {
        this.alertService = alertService;
        this.patientAccessService = patientAccessService;
    }

    /** This patient's alerts, newest first, optionally filtered by status. */
    @GetMapping("/api/patients/{patientUuid}/alerts")
    public List<AlertResponse> findByPatient(@PathVariable UUID patientUuid,
                                             @RequestParam(required = false) AlertStatus status) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return alertService.findByPatient(patient, status).stream().map(AlertMapper::toResponse).toList();
    }

    @GetMapping("/api/alerts/{uuid}")
    public AlertResponse findByUuid(@PathVariable UUID uuid) {
        return AlertMapper.toResponse(requireAlert(uuid, AccessLevel.VIEWER));
    }

    /** Marks an open alert resolved. 409 if it is already closed. */
    @PutMapping("/api/alerts/{uuid}/resolve")
    public AlertResponse resolve(@PathVariable UUID uuid) {
        return close(uuid, AlertStatus.RESOLVED);
    }

    /** Dismisses an open alert. 409 if it is already closed. */
    @PutMapping("/api/alerts/{uuid}/dismiss")
    public AlertResponse dismiss(@PathVariable UUID uuid) {
        return close(uuid, AlertStatus.DISMISSED);
    }

    private AlertResponse close(UUID uuid, AlertStatus closedAs) {
        requireAlert(uuid, AccessLevel.EDITOR);
        return AlertMapper.toResponse(alertService.close(uuid, closedAs, patientAccessService.requireCaller()));
    }

    private Alert requireAlert(UUID uuid, AccessLevel minimum) {
        Alert alert = alertService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(alert.getPatient(), minimum, () -> notFound(uuid));
        return alert;
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No alert with uuid " + uuid);
    }
}
