package com.sih.nivara.game.controller;

import com.sih.nivara.dto.mapper.GameResultMapper;
import com.sih.nivara.dto.response.GameResultResponse;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.game.dto.request.GameResultSubmitRequest;
import com.sih.nivara.game.service.result.GameResultSubmitService;
import com.sih.nivara.service.PatientAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * REST API for submitting game results after playing.
 *
 * <p>This controller provides a simplified interface for the frontend to submit
 * game results. It converts the simplified request format to the internal
 * GameResultCreateRequest format.
 *
 * <p>All endpoints require EDITOR access to the patient.
 */
@RestController
@RequestMapping("/api/patients/{patientUuid}/games")
public class GameResultSubmitController {

    private final GameResultSubmitService gameResultSubmitService;
    private final PatientAccessService patientAccessService;

    public GameResultSubmitController(GameResultSubmitService gameResultSubmitService,
                                      PatientAccessService patientAccessService) {
        this.gameResultSubmitService = gameResultSubmitService;
        this.patientAccessService = patientAccessService;
    }

    /**
     * Submits game results for a patient after they complete a game.
     *
     * <p>This endpoint accepts a simplified request format that includes:
     * <ul>
     *     <li>Game code (e.g., "MEMORY_MATCH")</li>
     *     <li>Difficulty level</li>
     *     <li>Duration, score, correct answers</li>
     *     <li>List of answers with question numbers and correctness</li>
     * </ul>
     *
     * <p>The response includes the full GameResult with all details,
     * including the computed accuracy.
     */
    @PostMapping("/submit-result")
    public ResponseEntity<GameResultResponse> submitResult(
            @PathVariable UUID patientUuid,
            @Valid @RequestBody GameResultSubmitRequest request) {

        // Authorize and get patient
        Patient patient = patientAccessService.requirePatient(patientUuid, AccessLevel.EDITOR);

        // Submit the result
        var gameResult = gameResultSubmitService.submitResult(patient, request);

        // Convert to response (using existing GameResultMapper)
        var response = GameResultMapper.toResponse(gameResult);

        // Return with Location header
        URI location = URI.create("/api/game-results/" + response.uuid());
        return ResponseEntity.created(location).body(response);
    }
}
