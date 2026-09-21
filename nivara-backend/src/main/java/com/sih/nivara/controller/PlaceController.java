package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.PlaceMapper;
import com.sih.nivara.dto.request.PlaceCreateRequest;
import com.sih.nivara.dto.request.PlaceUpdateRequest;
import com.sih.nivara.dto.response.PlaceResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.entity.Place;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.PatientAccessService;
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
 * <p>Every endpoint requires a bearer token and is authorized by {@link PatientAccessService}
 * against patient_caregivers: reading needs VIEWER and writing needs EDITOR access to the patient.
 * The item endpoints authorize against the item's own patient, and an item under a patient the
 * caller has no link to answers 404, exactly like one that does not exist. The creating account is
 * the one that authenticated the request, taken from {@link CurrentUserProvider}, never from the body.
 */
@RestController
public class PlaceController {

    private final PlaceService placeService;
    private final PatientAccessService patientAccessService;
    private final CurrentUserProvider currentUserProvider;

    public PlaceController(PlaceService placeService,
                           PatientAccessService patientAccessService,
                           CurrentUserProvider currentUserProvider) {
        this.placeService = placeService;
        this.patientAccessService = patientAccessService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Adds a place to this patient. Answers 201 with the new place and its Location, 404 when
     * the patient does not exist or the caller has no access to it, 403 without EDITOR access, or
     * 401 without a valid bearer token.
     */
    @PostMapping("/api/patients/{patientUuid}/places")
    public ResponseEntity<PlaceResponse> create(@PathVariable UUID patientUuid,
                                                @Valid @RequestBody PlaceCreateRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);
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
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return placeService.findByPatient(patient).stream()
                .map(PlaceMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/places/{uuid}")
    public PlaceResponse findByUuid(@PathVariable UUID uuid) {
        Place place = placeService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(place.getPatient(), AccessLevel.VIEWER, () -> notFound(uuid));
        return PlaceMapper.toResponse(place);
    }

    /** Replaces the editable fields of an existing place. */
    @PutMapping("/api/places/{uuid}")
    public PlaceResponse update(@PathVariable UUID uuid,
                                @Valid @RequestBody PlaceUpdateRequest request) {
        Place place = placeService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(place.getPatient(), AccessLevel.EDITOR, () -> notFound(uuid));
        return PlaceMapper.toResponse(placeService.update(uuid, request)
                .orElseThrow(() -> notFound(uuid)));
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No place with uuid " + uuid);
    }
}
