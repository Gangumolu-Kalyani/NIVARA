package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PatientMapper;
import com.sih.nivara.dto.request.PatientCreateRequest;
import com.sih.nivara.dto.request.PatientUpdateRequest;
import com.sih.nivara.dto.response.PatientResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Patient REST API. Patients are addressed by their public uuid, the identifier
 * {@link PatientResponse} carries; the bigint primary key stays inside the backend.
 *
 * <p>No entity crosses this boundary: requests arrive as DTOs and leave as DTOs, converted
 * by {@link PatientMapper}. The controller holds no rules of its own, only the wiring
 * between the HTTP layer and {@link PatientService}.
 *
 * <p>Every endpoint here requires a bearer token. The account that owns a new patient is the one
 * that authenticated the request, which the controller asks {@link CurrentUserProvider} for.
 * Which patients an account may reach is not checked yet; that arrives with caregiver
 * authorization.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final CurrentUserProvider currentUserProvider;

    public PatientController(PatientService patientService, CurrentUserProvider currentUserProvider) {
        this.patientService = patientService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Creates a patient owned by the caller's account. Answers 201 with the new patient and
     * its Location, or 401 without a valid bearer token.
     */
    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientCreateRequest request) {
        AppUser createdByUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No acting account is available yet"));

        Patient saved = patientService.save(PatientMapper.toEntity(request, createdByUser));
        PatientResponse body = PatientMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/patients/" + body.uuid())).body(body);
    }

    /**
     * Every patient, unfiltered and unpaged: there is no caller identity yet to scope the
     * list to, and soft-deleted rows are not excluded because that rule is still undecided.
     */
    @GetMapping
    public List<PatientResponse> findAll() {
        return patientService.findAll().stream()
                .map(PatientMapper::toResponse)
                .toList();
    }

    @GetMapping("/{uuid}")
    public PatientResponse findByUuid(@PathVariable UUID uuid) {
        return PatientMapper.toResponse(requirePatient(uuid));
    }

    /** Replaces the editable profile fields of an existing patient. */
    @PutMapping("/{uuid}")
    public PatientResponse update(@PathVariable UUID uuid,
                                  @Valid @RequestBody PatientUpdateRequest request) {
        Patient patient = requirePatient(uuid);
        PatientMapper.applyUpdate(request, patient);
        return PatientMapper.toResponse(patientService.save(patient));
    }

    private Patient requirePatient(UUID uuid) {
        return patientService.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No patient with uuid " + uuid));
    }
}
