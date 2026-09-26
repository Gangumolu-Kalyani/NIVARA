package com.sih.nivara.game.service.generator.familyrecognition;

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
import com.sih.nivara.service.PersonService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game generator for Family Member Recognition - identify family and familiar people.
 *
 * <p>This generator creates a game where the patient identifies people from photos
 * and answers questions about their relationship to the patient.
 */
@Service
public class FamilyRecognitionGenerator implements GameGenerator {

    private final PersonService personService;

    public FamilyRecognitionGenerator(PersonService personService) {
        this.personService = personService;
    }

    @Override
    public String getGameCode() {
        return "FAMILY_RECOGNITION";
    }

    @Override
    public boolean canGenerate(Patient patient, short difficulty) {
        // Get people for the patient
        var people = personService.findByPatient(patient);
        
        // Filter to only people marked for games
        long count = people.stream()
                .filter(Person::isIncludeInGames)
                .count();
        
        // Need at least 2 people for difficulty 1
        int minPeople = (difficulty == 1) ? 2 : (difficulty == 2) ? 3 : 3;
        return count >= minPeople;
    }

    @Override
    public GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game) {
        // Get all people for the patient
        var allPeople = personService.findByPatient(patient);
        
        // Filter to only people marked for games
        var people = allPeople.stream()
                .filter(Person::isIncludeInGames)
                .toList();
        
        // Determine number of people based on difficulty
        int numPeople = getPeopleForDifficulty(request.difficulty());
        
        // Select people for the game
        numPeople = Math.min(numPeople, people.size());
        var selectedPeople = people.subList(0, numPeople);
        
        // Create questions - identify person and relationship
        var questions = createRecognitionQuestions(selectedPeople, request.languageCode(), request.difficulty());
        
        // Create content references
        var contentReferences = createContentReferences(selectedPeople);
        
        // Create game instance ID
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
                        (short) questions.size(),
                        2,  // min people
                        4   // max people (for difficulty 3)
                )
        );
    }
    
    /**
     * Gets the number of people for a given difficulty level.
     */
    private int getPeopleForDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> 2;   // Recognize 2 familiar people
            case 2 -> 4;   // Recognize 3-4 people
            case 3 -> 4;   // More people + relationship questions
            case 4 -> 4;   // 4 people
            case 5 -> 4;   // 4 people (max)
            default -> 2;
        };
    }
    
    /**
     * Creates recognition questions for the selected people.
     */
    private List<GeneratedQuestion> createRecognitionQuestions(List<Person> people, String languageCode, short difficulty) {
        var questions = new ArrayList<GeneratedQuestion>();
        
        // Question 1: Identify each person
        for (int i = 0; i < people.size(); i++) {
            var person = people.get(i);
            var question = createIdentificationQuestion((short) (i + 1), person, languageCode);
            questions.add(question);
        }
        
        // For difficulty 3+, add relationship questions
        if (difficulty >= 3 && people.size() >= 2) {
            var relationshipQuestion = createRelationshipQuestion((short) (people.size() + 1), people, languageCode);
            questions.add(relationshipQuestion);
        }
        
        return questions;
    }
    
    /**
     * Creates an identification question for one person.
     */
    private GeneratedQuestion createIdentificationQuestion(short questionNumber, Person person, String languageCode) {
        var questionText = "Who is " + person.getFullName() + "?";
        
        var hint = GameHint.of(1, "This is a person you know.", com.sih.nivara.game.dto.response.HintType.TEXT);
        
        // Create options - this person + 3 random others if available
        var options = createIdentificationOptions(person, personService.findByPatient(person.getPatient()));
        
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
     * Creates options for identification (correct answer + distractors).
     */
    private List<Option> createIdentificationOptions(Person correctPerson, List<Person> allPeople) {
        var options = new ArrayList<Option>();
        
        // Add correct answer first
        options.add(new Option("PERSON", correctPerson.getFullName()));
        
        // Add 3 distractors from other people
        var distractors = allPeople.stream()
                .filter(p -> !p.getUuid().equals(correctPerson.getUuid()))
                .limit(3)
                .toList();
        
        for (var distractor : distractors) {
            options.add(new Option("PERSON", distractor.getFullName()));
        }
        
        return options;
    }
    
    /**
     * Creates a relationship question.
     */
    private GeneratedQuestion createRelationshipQuestion(short questionNumber, List<Person> people, String languageCode) {
        // Pick the first person as the target
        var targetPerson = people.get(0);
        var questionText = "How is " + targetPerson.getFullName() + " related to you?";
        
        var hint = GameHint.of(1, "Think about your family relationships.", com.sih.nivara.game.dto.response.HintType.TEXT);
        
        // Create relationship options
        var options = createRelationshipOptions(targetPerson);
        
        return new GeneratedQuestion(
                questionNumber,
                "RELATIONSHIP_RECALL",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Creates relationship options.
     */
    private List<Option> createRelationshipOptions(Person person) {
        var options = new ArrayList<Option>();
        
        // Add correct relationship if available
        if (person.getRelationship() != null) {
            options.add(new Option("RELATIONSHIP", person.getRelationship().name().replace("_", " ")));
        }
        
        // Add common distractors
        options.add(new Option("RELATIONSHIP", "Friend"));
        options.add(new Option("RELATIONSHIP", "Doctor"));
        options.add(new Option("RELATIONSHIP", "Neighbour"));
        
        return options;
    }
    
    /**
     * Creates content references for the selected people.
     */
    private List<GameContentReference> createContentReferences(List<Person> people) {
        var references = new ArrayList<GameContentReference>();
        
        for (var person : people) {
            references.add(new GameContentReference(
                    "PERSON",
                    person.getUuid().toString(),
                    person.getFullName(),
                    getRelationshipInfo(person)
            ));
        }
        
        return references;
    }
    
    /**
     * Gets a human-readable relationship string.
     */
    private String getRelationshipInfo(Person person) {
        if (person.getRelationship() == null) {
            return "Relationship not specified";
        }
        var sb = new StringBuilder(person.getRelationship().name().replace("_", " "));
        if (person.getRelationshipLabel() != null && !person.getRelationshipLabel().isEmpty()) {
            sb.append(" (").append(person.getRelationshipLabel()).append(")");
        }
        return sb.toString();
    }
}
