package com.sih.nivara.game.service.generator.matchit;

import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.Patient;
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
import com.sih.nivara.service.PlaceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game generator for Match It - visual association and recognition.
 *
 * <p>This generator creates a game where the patient matches objects/images with
 * related items. Difficulty controls the complexity of matching.
 */
@Service
public class MatchItGenerator implements GameGenerator {

    private final PersonalObjectService personalObjectService;
    private final PlaceService placeService;

    public MatchItGenerator(PersonalObjectService personalObjectService,
                            PlaceService placeService) {
        this.personalObjectService = personalObjectService;
        this.placeService = placeService;
    }

    @Override
    public String getGameCode() {
        return "MATCH_IT";
    }

    @Override
    public boolean canGenerate(Patient patient, short difficulty) {
        // Get objects and places for the patient
        var objects = personalObjectService.findByPatient(patient);
        var places = placeService.findByPatient(patient);
        
        long total = objects.size() + places.size();
        
        // Need at least 2 items for difficulty 1
        return total >= 2;
    }

    @Override
    public GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game) {
        // Get patient's content
        var objects = personalObjectService.findByPatient(patient);
        var places = placeService.findByPatient(patient);
        
        // Filter to only items marked for games
        var allItems = new ArrayList<GameContentItem>();
        
        for (var object : objects) {
            if (object.isIncludeInGames()) {
                allItems.add(new GameContentItem(object));
            }
        }
        for (var place : places) {
            if (place.isIncludeInGames()) {
                allItems.add(new GameContentItem(place));
            }
        }
        
        // Determine number of items based on difficulty
        int numItems = getItemsForDifficulty(request.difficulty());
        
        numItems = Math.min(numItems, allItems.size());
        var selectedItems = allItems.subList(0, numItems);
        
        // Create questions - match objects with related items
        var questions = createMatchingQuestions(selectedItems, request.languageCode(), request.difficulty());
        
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
                        2,  // min items
                        5   // max items
                )
        );
    }
    
    /**
     * Gets the number of items for a given difficulty level.
     */
    private int getItemsForDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> 3;   // 3 items - identical image matching
            case 2 -> 4;   // 4 items - object to related image
            case 3 -> 5;   // 5 items - image to word / word to image
            case 4 -> 5;   // 5 items
            case 5 -> 5;   // 5 items (max)
            default -> 3;
        };
    }
    
    /**
     * Creates matching questions based on difficulty level.
     */
    private List<GeneratedQuestion> createMatchingQuestions(List<GameContentItem> items, String languageCode, short difficulty) {
        var questions = new ArrayList<GeneratedQuestion>();
        
        // For all difficulties, create one main matching question
        var question = createMatchingQuestion((short) 1, items, languageCode, difficulty);
        questions.add(question);
        
        return questions;
    }
    
    /**
     * Creates a matching question.
     */
    private GeneratedQuestion createMatchingQuestion(short questionNumber, List<GameContentItem> items, String languageCode, short difficulty) {
        // Pick first item as the "target"
        var targetItem = items.get(0);
        
        // Question text varies by difficulty
        String questionText;
        switch (difficulty) {
            case 1:
                questionText = "Which one matches this item?";
                break;
            case 2:
                questionText = "Which item is related to this?";
                break;
            case 3:
                questionText = "Find the matching item:";
                break;
            default:
                questionText = "Match the items:";
                break;
        }
        
        // Create hint based on difficulty
        int hintLevel = getHintLevel(difficulty);
        var hint = GameHint.of(hintLevel, getDefaultHintText(), com.sih.nivara.game.dto.response.HintType.TEXT);
        
        // Create options - all items as potential matches
        var options = createOptions(items);
        
        return new GeneratedQuestion(
                questionNumber,
                "VISUAL_MATCH",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Gets the hint level based on difficulty.
     */
    private int getHintLevel(short difficulty) {
        return switch (difficulty) {
            case 1 -> 3;   // Easy - explicit help
            case 2 -> 2;   // Medium - visual clue
            case 3 -> 1;   // Hard - small clue
            default -> 1;
        };
    }
    
    /**
     * Gets default hint text.
     */
    private String getDefaultHintText() {
        return "Look for something similar or related.";
    }
    
    /**
     * Creates options for matching.
     */
    private List<Option> createOptions(List<GameContentItem> items) {
        var options = new ArrayList<Option>();
        
        for (var item : items) {
            options.add(createOption(item));
        }
        
        return options;
    }
    
    /**
     * Creates an option for an item.
     */
    private Option createOption(GameContentItem item) {
        return switch (item.item) {
            case PersonalObject p -> new Option("OBJECT", p.getName());
            case Place p -> new Option("PLACE", p.getName());
            default -> new Option("OTHER", "Unknown");
        };
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
            default -> new GameContentReference(
                    "OTHER",
                    "unknown",
                    "Unknown",
                    "Unknown item type"
            );
        };
    }
    
    /**
     * Helper class to wrap different entity types in a common type.
     */
    private static class GameContentItem {
        final Object item;
        
        GameContentItem(PersonalObject object) { this.item = object; }
        GameContentItem(Place place) { this.item = place; }
    }
}
