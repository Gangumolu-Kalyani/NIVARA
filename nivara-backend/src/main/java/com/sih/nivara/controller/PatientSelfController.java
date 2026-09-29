package com.sih.nivara.controller;

import com.sih.nivara.device.dto.mapper.PatientDeviceMapper;
import com.sih.nivara.dto.response.PatientSelfResponse;
import com.sih.nivara.security.CurrentPatientProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for a patient's own device, under /api/me. SecurityConfig lets only PATIENT accounts
 * in, and the patient always comes from the caller's own account through
 * {@link CurrentPatientProvider}, so there is no patient uuid to tamper with.
 */
@RestController
public class PatientSelfController {

    private final CurrentPatientProvider currentPatientProvider;

    public PatientSelfController(CurrentPatientProvider currentPatientProvider) {
        this.currentPatientProvider = currentPatientProvider;
    }

    /** The signed-in patient's own record. */
    @GetMapping("/api/me/patient")
    public PatientSelfResponse me() {
        return PatientDeviceMapper.toSelfResponse(currentPatientProvider.requirePatient());
    }
}
