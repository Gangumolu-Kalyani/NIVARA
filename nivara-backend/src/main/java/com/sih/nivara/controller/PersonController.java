package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PersonMapper;
import com.sih.nivara.dto.request.PersonCreateRequest;
import com.sih.nivara.dto.request.PersonUpdateRequest;
import com.sih.nivara.dto.response.PersonResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.Person;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.PatientAccessService;
import com.sih.nivara.service.PersonService;
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
 * People REST API: the people a patient knows, which memories then reference.
 *
 * <p>Same shape as the memory API, for the same reason: people.patient_id is mandatory and
 * immutable, so a person is created and listed within one patient, then addressed by its own
 * uuid. No entity crosses this boundary; {@link PersonMapper} converts both ways.
 *
 * <p>Every endpoint requires a bearer token and is authorized by {@link PatientAccessService}
 * against patient_caregivers: reading needs VIEWER and writing needs EDITOR access to the patient.
 * The item endpoints authorize against the item's own patient, and an item under a patient the
 * caller has no link to answers 404, exactly like one that does not exist. The creating account is
 * the one that authenticated the request, taken from {@link CurrentUserProvider}, never from the body.
 */
@RestController
public class PersonController {

    private final PersonService personService;
    private final PatientAccessService patientAccessService;
    private final CurrentUserProvider currentUserProvider;

    public PersonController(PersonService personService,
                            PatientAccessService patientAccessService,
                            CurrentUserProvider currentUserProvider) {
        this.personService = personService;
        this.patientAccessService = patientAccessService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Adds a person to this patient. Answers 201 with the new person and its Location, 404 when
     * the patient does not exist or the caller has no access to it, 403 without EDITOR access, or
     * 401 without a valid bearer token.
     */
    @PostMapping("/api/patients/{patientUuid}/people")
    public ResponseEntity<PersonResponse> create(@PathVariable UUID patientUuid,
                                                 @Valid @RequestBody PersonCreateRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);
        AppUser createdByUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No acting account is available yet"));

        Person saved = personService.save(PersonMapper.toEntity(request, patient, createdByUser));
        PersonResponse body = PersonMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/people/" + body.uuid())).body(body);
    }

    /** This patient's people, by name. Unpaged, and soft-deleted rows are not excluded. */
    @GetMapping("/api/patients/{patientUuid}/people")
    public List<PersonResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return personService.findByPatient(patient).stream()
                .map(PersonMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/people/{uuid}")
    public PersonResponse findByUuid(@PathVariable UUID uuid) {
        Person person = personService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(person.getPatient(), AccessLevel.VIEWER, () -> notFound(uuid));
        return PersonMapper.toResponse(person);
    }

    /** Replaces the editable fields of an existing person. */
    @PutMapping("/api/people/{uuid}")
    public PersonResponse update(@PathVariable UUID uuid,
                                 @Valid @RequestBody PersonUpdateRequest request) {
        Person person = personService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(person.getPatient(), AccessLevel.EDITOR, () -> notFound(uuid));
        return PersonMapper.toResponse(personService.update(uuid, request)
                .orElseThrow(() -> notFound(uuid)));
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No person with uuid " + uuid);
    }
}
