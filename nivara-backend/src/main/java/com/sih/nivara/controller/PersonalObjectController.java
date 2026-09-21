package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PersonalObjectMapper;
import com.sih.nivara.dto.request.PersonalObjectCreateRequest;
import com.sih.nivara.dto.request.PersonalObjectUpdateRequest;
import com.sih.nivara.dto.response.PersonalObjectResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.PatientService;
import com.sih.nivara.service.PersonalObjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Objects REST API: the everyday objects a patient uses, which memories then reference. The
 * path says "objects" to match the objects and objectUuids fields of the memory API; the
 * table is personal_objects.
 *
 * <p>Same shape as the memory API, for the same reason: personal_objects.patient_id is
 * mandatory and immutable, so an object is created and listed within one patient, then
 * addressed by its own uuid. No entity crosses this boundary; {@link PersonalObjectMapper}
 * converts both ways.
 *
 * <p>Development stage: these endpoints are unauthenticated, and the item endpoints do not yet
 * check that the caller may see the owning patient. That check needs the authenticated
 * principal and arrives with Spring Security. The creating account comes from
 * {@link CurrentUserProvider}, never from the request.
 */
@RestController
public class PersonalObjectController {

    private final PersonalObjectService personalObjectService;
    private final PatientService patientService;
    private final CurrentUserProvider currentUserProvider;

    public PersonalObjectController(PersonalObjectService personalObjectService,
                                    PatientService patientService,
                                    CurrentUserProvider currentUserProvider) {
        this.personalObjectService = personalObjectService;
        this.patientService = patientService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Adds an object to this patient. Answers 201 with the new object and its Location, 404
     * when the patient does not exist, or 503 while no caller can be established.
     */
    @PostMapping("/api/patients/{patientUuid}/objects")
    public ResponseEntity<PersonalObjectResponse> create(
            @PathVariable UUID patientUuid,
            @Valid @RequestBody PersonalObjectCreateRequest request) {
        Patient patient = requirePatient(patientUuid);
        AppUser createdByUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No acting account is available yet"));

        PersonalObject saved = personalObjectService.save(
                PersonalObjectMapper.toEntity(request, patient, createdByUser));
        PersonalObjectResponse body = PersonalObjectMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/objects/" + body.uuid())).body(body);
    }

    /** This patient's objects, by name. Unpaged, and soft-deleted rows are not excluded. */
    @GetMapping("/api/patients/{patientUuid}/objects")
    public List<PersonalObjectResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = requirePatient(patientUuid);
        return personalObjectService.findByPatient(patient).stream()
                .map(PersonalObjectMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/objects/{uuid}")
    public PersonalObjectResponse findByUuid(@PathVariable UUID uuid) {
        return PersonalObjectMapper.toResponse(personalObjectService.findByUuid(uuid)
                .orElseThrow(() -> notFound(uuid)));
    }

    /** Replaces the editable fields of an existing object. */
    @PutMapping("/api/objects/{uuid}")
    public PersonalObjectResponse update(@PathVariable UUID uuid,
                                         @Valid @RequestBody PersonalObjectUpdateRequest request) {
        return PersonalObjectMapper.toResponse(personalObjectService.update(uuid, request)
                .orElseThrow(() -> notFound(uuid)));
    }

    private Patient requirePatient(UUID patientUuid) {
        return patientService.findByUuid(patientUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No patient with uuid " + patientUuid));
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No object with uuid " + uuid);
    }
}
