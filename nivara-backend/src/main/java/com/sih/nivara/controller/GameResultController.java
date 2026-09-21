package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.GameResultMapper;
import com.sih.nivara.dto.request.GameResultCreateRequest;
import com.sih.nivara.dto.response.GameResultResponse;
import com.sih.nivara.dto.response.GameResultSummaryResponse;
import com.sih.nivara.entity.GameResult;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.service.GameResultService;
import com.sih.nivara.service.PatientAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Game result REST API: uploading a patient's attempts at games and reading them back.
 *
 * <p>Attempts are append-only, so there is no PUT or DELETE. They are recorded and listed within
 * one patient, and a single attempt is then addressed by its own uuid, the same shape as the
 * memory API. No entity crosses this boundary; {@link GameResultMapper} converts both ways, and
 * {@link GameResultService} applies the V3 rules inside one transaction.
 *
 * <p>Every endpoint requires a bearer token and is authorized by {@link PatientAccessService}
 * against patient_caregivers: recording needs EDITOR and reading needs VIEWER access to the
 * patient. The single-attempt endpoint authorizes against the attempt's own patient, and an attempt
 * under a patient the caller has no link to answers 404, exactly like one that does not exist.
 */
@RestController
public class GameResultController {

    private final GameResultService gameResultService;
    private final PatientAccessService patientAccessService;

    public GameResultController(GameResultService gameResultService, PatientAccessService patientAccessService) {
        this.gameResultService = gameResultService;
        this.patientAccessService = patientAccessService;
    }

    /**
     * Records one attempt, with its answers, for this patient. Answers 201 with the attempt and
     * its Location. Replaying an upload whose uuid is already stored for this patient answers 200
     * with the stored attempt and writes nothing. Otherwise 400 for an inconsistent attempt, 404
     * for an unknown or inaccessible patient, an unknown game or answer subject, 403 without EDITOR
     * access, 409 for a uuid owned by another patient, or 401 without a valid bearer token.
     */
    @PostMapping("/api/patients/{patientUuid}/game-results")
    public ResponseEntity<GameResultResponse> record(@PathVariable UUID patientUuid,
                                                     @Valid @RequestBody GameResultCreateRequest request) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);

        GameResultService.Recording recording = gameResultService.record(patient, request);
        GameResultResponse body = GameResultMapper.toResponse(recording.result());
        URI location = URI.create("/api/game-results/" + body.uuid());
        return recording.created()
                ? ResponseEntity.created(location).body(body)
                : ResponseEntity.ok().location(location).body(body);
    }

    /** This patient's attempts, most recently started first, without answers. Unpaged. */
    @GetMapping("/api/patients/{patientUuid}/game-results")
    public List<GameResultSummaryResponse> findByPatient(@PathVariable UUID patientUuid) {
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        return gameResultService.findByPatient(patient).stream()
                .map(GameResultMapper::toSummaryResponse)
                .toList();
    }

    /** One attempt with its answers, in question order; needs VIEWER access to its patient. */
    @GetMapping("/api/game-results/{uuid}")
    public GameResultResponse findByUuid(@PathVariable UUID uuid) {
        GameResult result = gameResultService.findByUuid(uuid).orElseThrow(() -> notFound(uuid));
        patientAccessService.requireAccess(result.getPatient(), AccessLevel.VIEWER, () -> notFound(uuid));
        return GameResultMapper.toResponse(result);
    }

    private static ResponseStatusException notFound(UUID uuid) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "No game result with uuid " + uuid);
    }
}
