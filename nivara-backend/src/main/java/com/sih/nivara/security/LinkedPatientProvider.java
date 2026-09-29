package com.sih.nivara.security;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.service.PatientService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * {@link CurrentPatientProvider} for the authenticated account: a PATIENT account resolves to the
 * patient that names it in patients.user_account_id. Any other account resolves to nothing.
 */
@Component
public class LinkedPatientProvider implements CurrentPatientProvider {

    private final CurrentUserProvider currentUserProvider;
    private final PatientService patientService;

    public LinkedPatientProvider(CurrentUserProvider currentUserProvider, PatientService patientService) {
        this.currentUserProvider = currentUserProvider;
        this.patientService = patientService;
    }

    @Override
    public Optional<Patient> currentPatient() {
        return currentUserProvider.currentUser().flatMap(patientService::findByUserAccount);
    }

    @Override
    public Patient requirePatient() {
        return currentPatient().orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a patient's own device can do this"));
    }
}
