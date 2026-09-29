package com.sih.nivara.game.service.generator.memorymatch;

import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.game.dto.request.GenerateGameRequest;
import com.sih.nivara.game.dto.response.GameContentReference;
import com.sih.nivara.game.dto.response.GameHint;
import com.sih.nivara.game.dto.response.GeneratedGameResponse;
import com.sih.nivara.game.dto.response.GeneratedQuestion;
import com.sih.nivara.game.dto.response.Option;
import com.sih.nivara.game.service.GameGenerator;
import com.sih.nivara.service.PatientAccessService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Game generator for Memory Match (Face and Name Match style game).
 *
 * <p>This generator creates a matching game where the patient matches faces (people) with names.
 * The game uses the patient's people data, and difficulty controls the number of pairs.
 */
@Service
public class MemoryMatchGenerator implements GameGenerator {

    private final PatientAccessService patientAccessService;

    public MemoryMatchGenerator(PatientAccessService patientAccessService) {
        this.patientAccessService = patientAccessService;
    }

    @Override
    public String getGameCode() {
        return "MEMORY_MATCH";
    }

    @Override
    public boolean canGenerate(Patient patient, short difficulty) {
        // For Memory Match, we need at least 2 pairs (4 people minimum for difficulty 1)
        // Each pair = 2 people (but we actually use 1 person per pair, matched with name)
        int minPeople = Math.min(4, (difficulty * 2));  // 2, 4, 6 people for difficulties 1, 2, 3
        
        var patientPeople = getIncludeInGamesPeople(patient);
        return patientPeople.size() >= minPeople;
    }

    @Override
    public GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game) {
        // Get patient's people
        var patientPeople = getIncludeInGamesPeople(patient);
        
        // Sort by name for consistent ordering
        patientPeople.sort((a, b) -> a.getFullName().compareTo(b.getFullName()));
        
        // Determine number of pairs based on difficulty
        int numPairs = getPairsForDifficulty(request.difficulty());
        
        // Select people for the game (up to numPairs)
        numPairs = Math.min(numPairs, patientPeople.size());
        
        // Create questions - each question is a matching challenge
        var questions = new ArrayList<GeneratedQuestion>();
        var contentReferences = new ArrayList<GameContentReference>();
        
        for (int i = 0; i < numPairs; i++) {
            var person = patientPeople.get(i);
            
            // Create the question
            var question = createMatchingQuestion(
                    (short) (i + 1),
                    person,
                    request.languageCode(),
                    game.getCognitiveDomain()
            );
            questions.add(question);
            
            // Add content reference
            contentReferences.add(new GameContentReference(
                    "PERSON",
                    person.getUuid().toString(),
                    person.getFullName(),
                    getRelationshipInfo(person)
            ));
        }
        
        // Create game instance ID (in real implementation, this would be stored)
        var gameInstanceId = UUID.randomUUID();
        
        return new GeneratedGameResponse(
                gameInstanceId,
                game.getCode(),
                game.getName(),
                request.difficulty(),
                questions,
                contentReferences,
                new com.sih.nivara.game.dto.response.GameMetadata(
                        game.getCognitiveDomain(),
                        (short) numPairs,
                        2,  // min pairs
                        6   // max pairs
                )
        );
    }
    
    /**
     * Gets the number of pairs for a given difficulty level.
     */
    private int getPairsForDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> 2;   // 2 pairs
            case 2 -> 4;   // 4 pairs
            case 3 -> 6;   // 6 pairs
            case 4 -> 8;   // 8 pairs
            case 5 -> 10;  // 10 pairs
            default -> 2;
        };
    }
    
    /**
     * Creates a matching question for a person.
     */
    private GeneratedQuestion createMatchingQuestion(short questionNumber, Person person,
                                                      String languageCode, 
                                                      com.sih.nivara.entity.enums.CognitiveDomain domain) {
        var questionText = "Who is " + person.getFullName() + "?";
        
        // Get hint based on available info
        var hint = createHintForPerson(person);
        
        // Create options - this person + 3 random others (if available)
        var options = createOptions(person);
        
        return new GeneratedQuestion(
                questionNumber,
                "FACE_NAME_MATCH",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Creates a hint for the person based on available information.
     */
    private GameHint createHintForPerson(Person person) {
        var hintBuilder = new StringBuilder();
        
        // Start with basic hint
        hintBuilder.append("This is someone you know.");
        
        // Add relationship info if available
        if (person.getRelationship() != null) {
            hintBuilder.append(" You know them as ").append(person.getRelationship().name());
        }
        
        // Add nickname if available
        if (person.getCalledAs() != null && !person.getCalledAs().isEmpty()) {
            hintBuilder.append(" They are called ").append(person.getCalledAs());
        }
        
        return GameHint.of(1, hintBuilder.toString(), com.sih.nivara.game.dto.response.HintType.TEXT);
    }
    
    /**
     * Creates multiple choice options for the question.
     */
    private List<Option> createOptions(Person correctPerson) {
        var options = new ArrayList<Option>();
        
        // Add correct answer first
        options.add(new Option("PERSON", correctPerson.getFullName()));
        
        // In a real implementation, we'd select 3 random other people
        // For now, return just the correct answer as a placeholder
        // The frontend would need to show the face and allow matching
        
        return options;
    }
    
    /**
     * Gets people that are marked as includeInGames.
     */
    private List<Person> getIncludeInGamesPeople(Patient patient) {
        // In real implementation, this would query the database
        // For now, return an empty list (the actual people would come from service layer)
        return new ArrayList<>();
    }
    
    /**
     * Gets a human-readable relationship string.
     */
    private String getRelationshipInfo(Person person) {
        if (person.getRelationship() == null) {
            return "Relationship not specified";
        }
        
        var sb = new StringBuilder(person.getRelationship().name());
        if (person.getRelationshipLabel() != null && !person.getRelationshipLabel().isEmpty()) {
            sb.append(" (").append(person.getRelationshipLabel()).append(")");
        }
        return sb.toString();
    }
}
