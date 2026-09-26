package com.sih.nivara.game.controller;

import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.enums.AccessLevel;
import com.sih.nivara.game.dto.response.GameHint;
import com.sih.nivara.service.PatientAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * REST API for retrieving hints in generated games.
 *
 * <p>Hints follow a progression:
 * <ul>
 *     <li>Level 0: No hint available</li>
 *     <li>Level 1: Small textual/audio clue</li>
 *     <li>Level 2: Stronger visual/audio clue</li>
 *     <li>Level 3: Explicit assistance</li>
 * </ul>
 *
 * <p>The current hint level is tracked by the frontend. When the patient struggles,
 * the frontend requests the next level hint. If the current level is already maxed out,
 * the response returns 404.
 */
@RestController
@RequestMapping("/api/patients/{patientUuid}/games/{instanceId}/hint")
public class GameHintController {

    private final PatientAccessService patientAccessService;

    public GameHintController(PatientAccessService patientAccessService) {
        this.patientAccessService = patientAccessService;
    }

    /**
     * Gets the next hint level for a question in a game instance.
     *
     * <p>The frontend tracks the current hint level and requests the next level when needed.
     * This allows progressive hinting based on the patient's struggles.
     */
    @GetMapping
    public GameHint getHint(@PathVariable UUID patientUuid,
                            @PathVariable UUID instanceId,
                            @RequestParam short questionNumber,
                            @RequestParam int currentLevel) {
        
        // Authorize access to patient
        patientAccessService.requirePatient(patientUuid, AccessLevel.VIEWER);
        
        // In a real implementation, this would:
        // 1. Look up the game instance (stored temporarily or in database)
        // 2. Find the specified question
        // 3. Calculate the next hint level based on currentLevel
        // 4. Generate the hint text for that level
        
        // For now, we simulate hint generation based on the pattern
        
        // Max hint level is 3 (explicit assistance)
        if (currentLevel >= 3) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No more hints available (current level: " + currentLevel + ")");
        }
        
        int nextLevel = currentLevel + 1;
        
        // Generate hint text based on level
        // In real implementation, this would use the actual question data
        String hintText = generateHintText(questionNumber, nextLevel);
        
        // Determine hint type based on level and content
        var hintType = getHintType(nextLevel);
        
        return new GameHint(nextLevel, hintText, hintType);
    }
    
    /**
     * Generates hint text for a given question and level.
     * 
     * <p>In a real implementation, this would use the actual question data and
     * potentially AI for natural language generation.
     */
    private String generateHintText(short questionNumber, int level) {
        return switch (level) {
            case 1 -> "Try looking for something similar or related.";
            case 2 -> "This is a person/place/object you know well.";
            case 3 -> "The answer should be clear from your personal data.";
            default -> "No hint available.";
        };
    }
    
    /**
     * Determines the hint type based on the hint level.
     */
    private com.sih.nivara.game.dto.response.HintType getHintType(int level) {
        return switch (level) {
            case 1 -> com.sih.nivara.game.dto.response.HintType.TEXT;
            case 2 -> com.sih.nivara.game.dto.response.HintType.VISUAL;
            case 3 -> com.sih.nivara.game.dto.response.HintType.TEXT;
            default -> com.sih.nivara.game.dto.response.HintType.TEXT;
        };
    }
}
