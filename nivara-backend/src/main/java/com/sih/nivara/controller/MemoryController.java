package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.MemoryMapper;
import com.sih.nivara.dto.request.MemoryCreateRequest;
import com.sih.nivara.dto.request.MemoryUpdateRequest;
import com.sih.nivara.dto.response.MemoryResponse;
import com.sih.nivara.entity.AppUser;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.MemoryService;
import com.sih.nivara.service.PatientAccessService;
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
 * Memory REST API.
 *
 * <p>Creating and listing are patient-scoped, because memories.patient_id is mandatory and
 * immutable: a memory exists only within one patient's record, and cannot later be moved.
 * An individual memory is then addressed by its own uuid, so reading or correcting one does
 * not require knowing whose it is. That is why the paths here are not all under one prefix
 * and the class carries no base @RequestMapping.
 *
 * <p>No entity crosses this boundary: requests arrive as DTOs and leave as DTOs, converted by
 * {@link MemoryMapper}. Resolving and linking a memory's place, people and objects belongs to
 * {@link MemoryService}, which does it inside one transaction; this controller only wires HTTP
 * to that service.
 *
 * <p>Every endpoint requires a bearer token and is authorized by {@link PatientAccessService}
 * against patient_caregivers: reading needs VIEWER and writing needs EDITOR access to the patient.
 * The item endpoints authorize against the memory's own patient, and a memory under a patient the
 * caller has no link to answers 404. The account recorded as having captured a memory is the one
 * that authenticated the request, taken from {@link CurrentUserProvider}.
 */
@RestController
public class MemoryController {

    private final MemoryService memoryService;
    private final PatientAccessService patientAccessService;
    private final CurrentUserProvider currentUserProvider;

    public MemoryController(MemoryService memoryService,
                            PatientAccessService patientAccessService,
                            CurrentUserProvider currentUserProvider) {
        this.memoryService = memoryService;
        this.patientAccessService = patientAccessService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Records a memory for this patient. Answers 201 with the new memory and its Location,
     * 404 when the patient or any referenced place, person or object is not this patient's or the
     * caller has no access to the patient, 403 without EDITOR access, or 401 without a valid
     * bearer token.
     */
    @PostMapping("/api/patients/{patientUuid}/memories")
    public ResponseEntity<MemoryResponse> create(@PathVariable UUID patientUuid,
                                                 @Valid @RequestBody MemoryCreateRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);
        AppUser recordedByUser = currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No acting account is available yet"));

        Memory saved = memoryService.create(patient, request, recordedByUser);
        MemoryResponse body = MemoryMapper.toResponse(saved);
        return ResponseEntity.created(URI.create("/api/memories/" + body.uuid())).body(body);
    }

    /**
     * This patient's memories, most recent first. Unpaged, and soft-deleted rows are not
     * excluded, because that rule is still undecided.
     */
    @GetMapping("/api/patients/{patientUuid}/memories")
    public List<MemoryResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return memoryService.findByPatient(patient).stream()
                .map(MemoryMapper::toResponse)
                .toList();
    }

    @GetMapping("/api/memories/{uuid}")
    public MemoryResponse findByUuid(@PathVariable UUID uuid) {
        Memory memory = memoryService.findByUuid(uuid).orElseThrow(() -> notFoundMemory(uuid));
        patientAccessService.requireAccess(memory.getPatient(), AccessLevel.VIEWER, () -> notFoundMemory(uuid));
        return MemoryMapper.toResponse(memory);
    }

    /** Replaces the editable fields and the links of an existing memory. */
    @PutMapping("/api/memories/{uuid}")
    public MemoryResponse update(@PathVariable UUID uuid,
                                 @Valid @RequestBody MemoryUpdateRequest request) {
        Memory memory = memoryService.findByUuid(uuid).orElseThrow(() -> notFoundMemory(uuid));
        patientAccessService.requireAccess(memory.getPatient(), AccessLevel.EDITOR, () -> notFoundMemory(uuid));
        return MemoryMapper.toResponse(memoryService.update(uuid, request)
                .orElseThrow(() -> notFoundMemory(uuid)));
    }

    private static ResponseStatusException notFoundMemory(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No memory with uuid " + uuid);
    }
}
