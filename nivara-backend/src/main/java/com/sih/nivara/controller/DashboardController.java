package com.sih.nivara.controller;

import com.sih.nivara.dto.response.DailySummaryResponse;
import com.sih.nivara.dto.response.DashboardSummaryResponse;
import com.sih.nivara.dto.response.ProgressResponse;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.DashboardService;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.service.ReminderSchedule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Caregiver dashboard REST API: today's summary, cognitive progress and the daily summary.
 * Read-only views over reminders, alerts and game results; every endpoint needs VIEWER access.
 */
@RestController
public class DashboardController {

    private static final int MAX_RANGE_DAYS = 90;

    private final DashboardService dashboardService;
    private final PatientAccessService patientAccessService;

    public DashboardController(DashboardService dashboardService, PatientAccessService patientAccessService) {
        this.dashboardService = dashboardService;
        this.patientAccessService = patientAccessService;
    }

    @GetMapping("/api/patients/{patientUuid}/dashboard")
    public DashboardSummaryResponse summary(@PathVariable UUID patientUuid) {
        return dashboardService.summary(patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER));
    }

    /** Game performance over the last range days, today included; 7 by default, at most 90. */
    @GetMapping("/api/patients/{patientUuid}/progress")
    public ProgressResponse progress(@PathVariable UUID patientUuid,
                                     @RequestParam(defaultValue = "7") int range) {
        if (range < 1 || range > MAX_RANGE_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "range must be between 1 and " + MAX_RANGE_DAYS + " days");
        }
        return dashboardService.progress(patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER), range);
    }

    /** A plain-language summary of one of the patient's days; today when date is omitted. */
    @GetMapping("/api/patients/{patientUuid}/daily-summary")
    public DailySummaryResponse dailySummary(
            @PathVariable UUID patientUuid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        LocalDate day = date != null ? date : ReminderSchedule.localDate(patient, Instant.now());
        return dashboardService.dailySummary(patient, day);
    }
}
