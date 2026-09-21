package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.GameResultMapper;
import com.sih.nivara.dto.request.GameResultCreateRequest;
import com.sih.nivara.dto.response.GameResultResponse;
import com.sih.nivara.dto.response.GameResultSummaryResponse;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.security.CurrentUserProvider;
import com.sih.nivara.service.GameResultService;
import com.sih.nivara.service.PatientService;
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
 * <p>Development stage: these endpoints are unauthenticated, and the item endpoint does not yet
 * check that the caller may see the owning patient. Recording still requires an acting account
 * from {@link CurrentUserProvider}, like every other write, although game_results stores no
 * creator: once Spring Security is added, that account is the patient's own login or a caregiver.
 */
@RestController
public class GameResultController {

    private final GameResultService gameResultService;
    private final PatientService patientService;
    private final CurrentUserProvider currentUserProvider;

    public GameResultController(GameResultService gameResultService,
                                PatientService patientService,
                                CurrentUserProvider currentUserProvider) {
        this.gameResultService = gameResultService;
        this.patientService = patientService;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Records one attempt, with its answers, for this patient. Answers 201 with the attempt and
     * its Location. Replaying an upload whose uuid is already stored for this patient answers 200
     * with the stored attempt and writes nothing. Otherwise 400 for an inconsistent attempt, 404
     * for an unknown patient, game or answer subject, 409 for a uuid owned by another patient,
     * or 503 while no caller can be established.
     */
    @PostMapping("/api/patients/{patientUuid}/game-results")
    public ResponseEntity<GameResultResponse> record(@PathVariable UUID patientUuid,
                                                     @Valid @RequestBody GameResultCreateRequest request) {
        Patient patient = requirePatient(patientUuid);
        currentUserProvider.currentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "No acting account is available yet"));

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
        Patient patient = requirePatient(patientUuid);
        return gameResultService.findByPatient(patient).stream()
                .map(GameResultMapper::toSummaryResponse)
                .toList();
    }

    /** One attempt with its answers, in question order. */
    @GetMapping("/api/game-results/{uuid}")
    public GameResultResponse findByUuid(@PathVariable UUID uuid) {
        return GameResultMapper.toResponse(gameResultService.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No game result with uuid " + uuid)));
    }

    private Patient requirePatient(UUID patientUuid) {
        return patientService.findByUuid(patientUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No patient with uuid " + patientUuid));
    }
}
