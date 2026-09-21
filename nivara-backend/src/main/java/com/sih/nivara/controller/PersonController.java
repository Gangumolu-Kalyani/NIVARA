package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PersonMapper;
import com.sih.nivara.dto.request.PersonCreateRequest;
import com.sih.nivara.dto.request.PersonUpdateRequest;
import com.sih.nivara.dto.response.PersonResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.PatientService;
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
 * <p>Development stage: these endpoints are unauthenticated, and the item endpoints do not yet
 * check that the caller may see the owning patient. That check needs the authenticated
 * principal and arrives with Spring Security. The creating account comes from
 * {@link CurrentUserProvider}, never from the request.
 */
@RestController
public class PersonController {

    private final PersonService personService;
    private final PatientService patientService;
    private final CurrentUserProvider currentUserProvider;

    public PersonController(PersonService personService,
                            PatientService patientService,
                            CurrentUserProvider currentUserProvider) {
        this.personService = personService;
        this.patientService = patientService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Adds a person to this patient. Answers 201 with the new person and its Location, 404 when
     * the patient does not exist, or 503 while no caller can be established.
     */
    @PostMapping("/api/patients/{patientUuid}/people")
    public ResponseEntity<PersonResponse> create(@PathVariable UUID patientUuid,
                                                 @Valid @RequestBody PersonCreateRequest request) {
        Patient patient = requirePatient(patientUuid);
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
        Patient patient = requirePatient(patientUuid);
        return personService.findByPatient(patient).stream()
                .map(PersonMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/people/{uuid}")
    public PersonResponse findByUuid(@PathVariable UUID uuid) {
        return PersonMapper.toResponse(personService.findByUuid(uuid)
                .orElseThrow(() -> notFound(uuid)));
    }

    /** Replaces the editable fields of an existing person. */
    @PutMapping("/api/people/{uuid}")
    public PersonResponse update(@PathVariable UUID uuid,
                                 @Valid @RequestBody PersonUpdateRequest request) {
        return PersonMapper.toResponse(personService.update(uuid, request)
                .orElseThrow(() -> notFound(uuid)));
    }

    private Patient requirePatient(UUID patientUuid) {
        return patientService.findByUuid(patientUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No patient with uuid " + patientUuid));
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No person with uuid " + uuid);
    }
}
