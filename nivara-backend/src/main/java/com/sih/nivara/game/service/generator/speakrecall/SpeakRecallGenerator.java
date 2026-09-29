package com.sih.nivara.game.service.generator.speakrecall;

import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Person;
import com.sih.nivara.entity.PersonalObject;
import com.sih.nivara.entity.Place;
import com.sih.nivara.game.dto.request.GenerateGameRequest;
import com.sih.nivara.game.dto.response.GameContentReference;
import com.sih.nivara.game.dto.response.GameHint;
import com.sih.nivara.game.dto.response.GeneratedGameResponse;
import com.sih.nivara.game.dto.response.GeneratedQuestion;
import com.sih.nivara.game.dto.response.Option;
import com.sih.nivara.game.service.GameGenerator;
import com.sih.nivara.service.PersonalObjectService;
import com.sih.nivara.service.PersonService;
import com.sih.nivara.service.PlaceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game generator for Speak & Recall - voice-based recall and recognition.
 *
 * <p>This generator creates a multi-level voice-based game:
 * - Level 1 (Name It): Show object/image, patient says what it is
 * - Level 2 (Complete Sentence): "I brush my teeth with a..."
 * - Level 3 (Recall & Answer): Ask simple questions
 * - Level 4 (Remember & Speak): Show image briefly, hide, patient recalls
 * - Level 5 (Sing & Recall): Use familiar song segment
 */
@Service
public class SpeakRecallGenerator implements GameGenerator {

    private final PersonService personService;
    private final PlaceService placeService;
    private final PersonalObjectService personalObjectService;

    public SpeakRecallGenerator(PersonService personService, PlaceService placeService,
                                PersonalObjectService personalObjectService) {
        this.personService = personService;
        this.placeService = placeService;
        this.personalObjectService = personalObjectService;
    }

    @Override
    public String getGameCode() {
        return "SPEAK_RECALL";
    }

    @Override
    public boolean canGenerate(Patient patient, short difficulty) {
        // Get all types of content
        var people = personService.findByPatient(patient);
        var places = placeService.findByPatient(patient);
        var objects = personalObjectService.findByPatient(patient);
        
        long total = people.size() + places.size() + objects.size();
        
        // Need at least 1 item for level 1
        return total >= 1;
    }

    @Override
    public GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game) {
        // Get patient's content
        var people = personService.findByPatient(patient);
        var places = placeService.findByPatient(patient);
        var objects = personalObjectService.findByPatient(patient);
        
        // Filter to only items marked for games
        var allItems = new ArrayList<GameContentItem>();
        
        for (var person : people) {
            if (person.isIncludeInGames()) {
                allItems.add(new GameContentItem(person));
            }
        }
        for (var place : places) {
            if (place.isIncludeInGames()) {
                allItems.add(new GameContentItem(place));
            }
        }
        for (var object : objects) {
            if (object.isIncludeInGames()) {
                allItems.add(new GameContentItem(object));
            }
        }
        
        // Determine number of items based on difficulty
        int numItems = getItemsForDifficulty(request.difficulty());
        
        numItems = Math.min(numItems, allItems.size());
        var selectedItems = allItems.subList(0, numItems);
        
        // Create questions based on difficulty level
        var questions = createVoiceQuestions(selectedItems, request.languageCode(), request.difficulty());
        
        // Create content references
        var contentReferences = createContentReferences(selectedItems);
        
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
                        1,  // min items
                        5   // max items
                )
        );
    }
    
    /**
     * Gets the number of items for a given difficulty level.
     */
    private int getItemsForDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> 1;   // Level 1: Name It - 1 item
            case 2 -> 2;   // Level 2: Complete Sentence - 2 items
            case 3 -> 3;   // Level 3: Recall & Answer - 3 items
            case 4 -> 3;   // Level 4: Remember & Speak - 3 items
            case 5 -> 3;   // Level 5: Sing & Recall - 3 items
            default -> 1;
        };
    }
    
    /**
     * Creates voice-based questions based on difficulty level.
     */
    private List<GeneratedQuestion> createVoiceQuestions(List<GameContentItem> items, String languageCode, short difficulty) {
        var questions = new ArrayList<GeneratedQuestion>();
        
        switch (difficulty) {
            case 1:
                // Level 1: Name It - patient says what object/person/place it is
                var level1Question = createNameItQuestion((short) 1, items.get(0), languageCode);
                questions.add(level1Question);
                break;
                
            case 2:
                // Level 2: Complete the Sentence - patient completes sentence with item name
                var level2Question = createSentenceCompletionQuestion((short) 1, items, languageCode);
                questions.add(level2Question);
                break;
                
            case 3:
                // Level 3: Recall & Answer - ask simple questions about items
                for (int i = 0; i < Math.min(3, items.size()); i++) {
                    var question = createQuestionAnswerQuestion((short) (i + 1), items.get(i), languageCode);
                    questions.add(question);
                }
                break;
                
            case 4:
            case 5:
                // Level 4 & 5: Remember & Speak / Sing & Recall - show image, patient recalls
                for (int i = 0; i < Math.min(3, items.size()); i++) {
                    var question = createRememberSpeakQuestion((short) (i + 1), items.get(i), languageCode, difficulty);
                    questions.add(question);
                }
                break;
        }
        
        return questions;
    }
    
    /**
     * Level 1: Name It - show object, patient says what it is.
     */
    private GeneratedQuestion createNameItQuestion(short questionNumber, GameContentItem item, String languageCode) {
        var questionText = switch (item.item) {
            case PersonalObject p -> "What is this object called?";
            case Place p -> "What is this place called?";
            case Person p -> "Who is this person?";
            default -> "What do you see?";
        };
        
        var hint = GameHint.of(3, "Take your time and say the name.", com.sih.nivara.game.dto.response.HintType.TEXT);
        
        var options = List.of(createOption(item));
        
        return new GeneratedQuestion(
                questionNumber,
                "VOICE_NAME_IT",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Level 2: Complete the Sentence - patient completes sentence with item name.
     */
    private GeneratedQuestion createSentenceCompletionQuestion(short questionNumber, List<GameContentItem> items, String languageCode) {
        var targetItem = items.get(0);
        var questionText = switch (targetItem.item) {
            case PersonalObject p -> "I use a... to help me.";
            case Place p -> "I went to... recently.";
            case Person p -> "I was with... yesterday.";
            default -> "I remember...";
        };
        
        var hint = GameHint.of(2, "The answer is one of the items shown.", com.sih.nivara.game.dto.response.HintType.TEXT);
        
        var options = createOptions(items);
        
        return new GeneratedQuestion(
                questionNumber,
                "VOICE_SENTENCE_COMPLETE",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Level 3: Recall & Answer - ask simple questions about items.
     */
    private GeneratedQuestion createQuestionAnswerQuestion(short questionNumber, GameContentItem item, String languageCode) {
        var questionText = switch (item.item) {
            case PersonalObject p -> "Where do you usually keep your " + p.getName() + "?";
            case Place p -> "What do you do at " + p.getName() + "?";
            case Person p -> "How do you know " + p.getFullName() + "?";
            default -> "Tell me about this.";
        };
        
        var hint = GameHint.of(1, "Think about your experiences with this.", com.sih.nivara.game.dto.response.HintType.TEXT);
        
        return new GeneratedQuestion(
                questionNumber,
                "VOICE_RECALL_ANSWER",
                questionText,
                languageCode,
                hint,
                List.of()  // Open-ended, no predefined options
        );
    }
    
    /**
     * Level 4 & 5: Remember & Speak / Sing & Recall - show image, patient recalls.
     */
    private GeneratedQuestion createRememberSpeakQuestion(short questionNumber, GameContentItem item, String languageCode, short difficulty) {
        var questionText = switch (difficulty) {
            case 4 -> "Remember what you just saw. What was it?";
            case 5 -> "Remember the song. What comes next?";
            default -> "What did you just see?";
        };
        
        var hintLevel = (difficulty == 5) ? 3 : 2;  // Level 5 gets more help
        var hint = GameHint.of(hintLevel, "Think carefully about what you saw.", com.sih.nivara.game.dto.response.HintType.AUDIO);
        
        var options = List.of(createOption(item));
        
        return new GeneratedQuestion(
                questionNumber,
                "VOICE_REMEMBER_SPEAK",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Creates an option for an item.
     */
    private Option createOption(GameContentItem item) {
        return switch (item.item) {
            case PersonalObject p -> new Option("OBJECT", p.getName());
            case Place p -> new Option("PLACE", p.getName());
            case Person p -> new Option("PERSON", p.getFullName());
            default -> new Option("OTHER", "Unknown");
        };
    }
    
    /**
     * Creates options from a list of items.
     */
    private List<Option> createOptions(List<GameContentItem> items) {
        var options = new ArrayList<Option>();
        for (var item : items) {
            options.add(createOption(item));
        }
        return options;
    }
    
    /**
     * Creates content references for the selected items.
     */
    private List<GameContentReference> createContentReferences(List<GameContentItem> items) {
        var references = new ArrayList<GameContentReference>();
        
        for (var item : items) {
            references.add(createContentReference(item));
        }
        
        return references;
    }
    
    /**
     * Creates a content reference for an item.
     */
    private GameContentReference createContentReference(GameContentItem item) {
        return switch (item.item) {
            case PersonalObject p -> new GameContentReference(
                    "OBJECT",
                    p.getUuid().toString(),
                    p.getName(),
                    p.getCategory().name().replace("_", " ")
            );
            case Place p -> new GameContentReference(
                    "PLACE",
                    p.getUuid().toString(),
                    p.getName(),
                    p.getPlaceType().name().replace("_", " ")
            );
            case Person p -> new GameContentReference(
                    "PERSON",
                    p.getUuid().toString(),
                    p.getFullName(),
                    getRelationshipInfo(p)
            );
            default -> new GameContentReference(
                    "OTHER",
                    "unknown",
                    "Unknown",
                    "Unknown item type"
            );
        };
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
    
    /**
     * Helper class to wrap different entity types in a common type.
     */
    private static class GameContentItem {
        final Object item;
        
        GameContentItem(Person person) { this.item = person; }
        GameContentItem(Place place) { this.item = place; }
        GameContentItem(PersonalObject object) { this.item = object; }
    }
}
