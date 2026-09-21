package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PlaceMapper;
import com.sih.nivara.dto.request.PlaceCreateRequest;
import com.sih.nivara.dto.request.PlaceUpdateRequest;
import com.sih.nivara.dto.response.PlaceResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Place;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.PatientService;
import com.sih.nivara.service.PlaceService;
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
 * Places REST API: the places that matter to a patient, where memories then happen.
 *
 * <p>Same shape as the memory API, for the same reason: places.patient_id is mandatory and
 * immutable, so a place is created and listed within one patient, then addressed by its own
 * uuid. No entity crosses this boundary; {@link PlaceMapper} converts both ways.
 *
 * <p>Development stage: these endpoints are unauthenticated, and the item endpoints do not yet
 * check that the caller may see the owning patient. That check needs the authenticated
 * principal and arrives with Spring Security. The creating account comes from
 * {@link CurrentUserProvider}, never from the request.
 */
@RestController
public class PlaceController {

    private final PlaceService placeService;
    private final PatientService patientService;
    private final CurrentUserProvider currentUserProvider;

    public PlaceController(PlaceService placeService,
                           PatientService patientService,
                           CurrentUserProvider currentUserProvider) {
        this.placeService = placeService;
        this.patientService = patientService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Adds a place to this patient. Answers 201 with the new place and its Location, 404 when
     * the patient does not exist, or 503 while no caller can be established.
     */
    @PostMapping("/api/patients/{patientUuid}/places")
    public ResponseEntity<PlaceResponse> create(@PathVariable UUID patientUuid,
                                                @Valid @RequestBody PlaceCreateRequest request) {
        Patient patient = requirePatient(patientUuid);
        AppUser createdByUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No acting account is available yet"));

        Place saved = placeService.save(PlaceMapper.toEntity(request, patient, createdByUser));
        PlaceResponse body = PlaceMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/places/" + body.uuid())).body(body);
    }

    /** This patient's places, by name. Unpaged, and soft-deleted rows are not excluded. */
    @GetMapping("/api/patients/{patientUuid}/places")
    public List<PlaceResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = requirePatient(patientUuid);
        return placeService.findByPatient(patient).stream()
                .map(PlaceMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/places/{uuid}")
    public PlaceResponse findByUuid(@PathVariable UUID uuid) {
        return PlaceMapper.toResponse(placeService.findByUuid(uuid)
                .orElseThrow(() -> notFound(uuid)));
    }

    /** Replaces the editable fields of an existing place. */
    @PutMapping("/api/places/{uuid}")
    public PlaceResponse update(@PathVariable UUID uuid,
                                @Valid @RequestBody PlaceUpdateRequest request) {
        return PlaceMapper.toResponse(placeService.update(uuid, request)
                .orElseThrow(() -> notFound(uuid)));
    }

    private Patient requirePatient(UUID patientUuid) {
        return patientService.findByUuid(patientUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No patient with uuid " + patientUuid));
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No place with uuid " + uuid);
    }
}
