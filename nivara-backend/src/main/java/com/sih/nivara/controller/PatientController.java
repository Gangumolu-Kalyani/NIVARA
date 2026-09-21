package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PatientMapper;
import com.sih.nivara.dto.request.PatientCreateRequest;
import com.sih.nivara.dto.request.PatientUpdateRequest;
import com.sih.nivara.dto.response.PatientResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.enums.RelationshipType;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.service.PatientAccessService.PatientAccess;
import com.sih.nivara.service.PatientService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 * <p>Every endpoint requires a bearer token and is authorized by {@link PatientAccessService}
 * against patient_caregivers: reading needs VIEWER, changing the profile needs EDITOR, and a patient
 * the caller has no link to answers 404. Creating a patient makes the caller its OWNER and primary
 * caregiver, and the list shows only the patients the caller can reach.
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;
    private final PatientAccessService patientAccessService;

    public PatientController(PatientService patientService, PatientAccessService patientAccessService) {
        this.patientService = patientService;
        this.patientAccessService = patientAccessService;
    }

    /**
     * Creates a patient whose OWNER and primary caregiver is the caller, in one transaction.
     * Answers 201 with the new patient and its Location, or 401 without a valid bearer token.
     */
    @PostMapping
    public ResponseEntity<PatientResponse> create(@Valid @RequestBody PatientCreateRequest request) {
        AppUser creator = patientAccessService.requireCaller();
        RelationshipType relationship = request.relationship() == null
                ? RelationshipType.CAREGIVER
                : request.relationship();

        Patient saved = patientService.createWithOwner(PatientMapper.toEntity(request, creator), creator, relationship);
        PatientResponse body = PatientMapper.toResponse(saved, AccessLevel.OWNER);
        return ResponseEntity.created(URI.create("/api/patients/" + body.uuid())).body(body);
    }

    /**
     * The patients the caller has access to, by name, each with the caller's own access level.
     * Unpaged, and soft-deleted rows are not excluded because that rule is still undecided.
     */
    @GetMapping
    public List<PatientResponse> findAll() {
        return patientAccessService.accessiblePatients().stream()
                .map(access -> PatientMapper.toResponse(access.patient(), access.accessLevel()))
                .toList();
    }

    /** One patient; needs VIEWER access. */
    @GetMapping("/{uuid}")
    public PatientResponse findByUuid(@PathVariable UUID uuid) {
        PatientAccess access = patientAccessService.requirePatientAccess(uuid, AccessLevel.VIEWER);
        return PatientMapper.toResponse(access.patient(), access.accessLevel());
    }

    /** Replaces the editable profile fields of an existing patient; needs EDITOR access. */
    @PutMapping("/{uuid}")
    public PatientResponse update(@PathVariable UUID uuid,
                                  @Valid @RequestBody PatientUpdateRequest request) {
        PatientAccess access = patientAccessService.requirePatientAccess(uuid, AccessLevel.EDITOR);
        Patient patient = access.patient();
        PatientMapper.applyUpdate(request, patient);
        return PatientMapper.toResponse(patientService.save(patient), access.accessLevel());
    }
}
