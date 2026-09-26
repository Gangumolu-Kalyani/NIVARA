package com.sih.nivara.game.service.generator.revealremember;

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
 * Game generator for Reveal & Remember - image recognition through gradual reveal.
 *
 * <p>This generator creates a game where images are hidden behind tiles and revealed
 * gradually. The patient identifies what they see at each stage.
 */
@Service
public class RevealRememberGenerator implements GameGenerator {

    private final PersonService personService;
    private final PlaceService placeService;
    private final PersonalObjectService personalObjectService;

    public RevealRememberGenerator(PersonService personService, PlaceService placeService,
                                   PersonalObjectService personalObjectService) {
        this.personService = personService;
        this.placeService = placeService;
        this.personalObjectService = personalObjectService;
    }

    @Override
    public String getGameCode() {
        return "REVEAL_REMEMBER";
    }

    @Override
    public boolean canGenerate(Patient patient, short difficulty) {
        // Get all people, places, and objects marked for games
        var people = personService.findByPatient(patient);
        var places = placeService.findByPatient(patient);
        var objects = personalObjectService.findByPatient(patient);
        
        long total = people.size() + places.size() + objects.size();
        
        // Need at least 1 item for any difficulty
        return total >= 1;
    }

    @Override
    public GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game) {
        // Get patient's content
        var people = personService.findByPatient(patient);
        var places = placeService.findByPatient(patient);
        var objects = personalObjectService.findByPatient(patient);
        
        // Filter to only items marked for games and convert to common type
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
        
        // Determine number of images based on difficulty
        int numImages = getImagesForDifficulty(request.difficulty());
        
        // Select items for the game
        numImages = Math.min(numImages, allItems.size());
        var selectedItems = allItems.subList(0, numImages);
        
        // Create questions - each question shows an image to identify
        var questions = createRevealQuestions(selectedItems, request.languageCode(), request.difficulty());
        
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
                        1,  // min images
                        6   // max images (for difficulty 3)
                )
        );
    }
    
    /**
     * Gets the number of images for a given difficulty level.
     */
    private int getImagesForDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> 1;   // 1 image, mostly visible
            case 2 -> 3;   // 3 images, half hidden
            case 3 -> 5;   // 5 images, mostly hidden
            case 4 -> 6;   // 6 images
            case 5 -> 6;   // 6 images (max)
            default -> 1;
        };
    }
    
    /**
     * Creates reveal questions for the selected items.
     */
    private List<GeneratedQuestion> createRevealQuestions(List<GameContentItem> items, String languageCode, short difficulty) {
        var questions = new ArrayList<GeneratedQuestion>();
        
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            var question = createRevealQuestion((short) (i + 1), item, languageCode, difficulty);
            questions.add(question);
        }
        
        return questions;
    }
    
    /**
     * Creates a reveal question for one item.
     */
    private GeneratedQuestion createRevealQuestion(short questionNumber, GameContentItem item, String languageCode, short difficulty) {
        // Determine hint level based on difficulty
        int hintLevel = getHintLevel(difficulty);
        
        var questionText = switch (item.item) {
            case Person p -> "Who is this person?";
            case Place p -> "Where is this place?";
            case PersonalObject p -> "What is this object?";
            default -> "What do you see?";
        };
        
        var hint = GameHint.of(hintLevel, getDefaultHintText(item), 
                com.sih.nivara.game.dto.response.HintType.VISUAL);
        
        // Create options - just this item for now
        var options = List.of(createOption(item));
        
        return new GeneratedQuestion(
                questionNumber,
                "IMAGE_RECOGNITION",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Gets the hint level based on difficulty (higher difficulty = lower hint level = harder).
     */
    private int getHintLevel(short difficulty) {
        // Difficulty 1: hint level 3 (easy - explicit help)
        // Difficulty 2: hint level 2 (medium - visual clue)
        // Difficulty 3: hint level 1 (hard - small clue)
        return switch (difficulty) {
            case 1 -> 3;
            case 2 -> 2;
            case 3 -> 1;
            case 4 -> 1;
            case 5 -> 1;
            default -> 3;
        };
    }
    
    /**
     * Gets default hint text for an item.
     */
    private String getDefaultHintText(GameContentItem item) {
        return switch (item.item) {
            case Person p -> "This is someone you know.";
            case Place p -> "This is a place you've been to.";
            case PersonalObject p -> "This is an object you use.";
            default -> "Take a guess!";
        };
    }
    
    /**
     * Creates an option for an item.
     */
    private Option createOption(GameContentItem item) {
        return switch (item.item) {
            case Person p -> new Option("PERSON", p.getFullName());
            case Place p -> new Option("PLACE", p.getName());
            case PersonalObject p -> new Option("OBJECT", p.getName());
            default -> new Option("OTHER", "Unknown");
        };
    }
    
    /**
     * Creates content references for the selected items.
     */
    private List<GameContentReference> createContentReferences(List<GameContentItem> items) {
        var references = new ArrayList<GameContentReference>();
        
        for (var item : items) {
            var reference = createContentReference(item);
            references.add(reference);
        }
        
        return references;
    }
    
    /**
     * Creates a content reference for an item.
     */
    private GameContentReference createContentReference(GameContentItem item) {
        return switch (item.item) {
            case Person p -> new GameContentReference(
                    "PERSON",
                    p.getUuid().toString(),
                    p.getFullName(),
                    getRelationshipInfo(p)
            );
            case Place p -> new GameContentReference(
                    "PLACE",
                    p.getUuid().toString(),
                    p.getName(),
                    p.getPlaceType().name().replace("_", " ")
            );
            case PersonalObject p -> new GameContentReference(
                    "OBJECT",
                    p.getUuid().toString(),
                    p.getName(),
                    p.getCategory().name().replace("_", " ")
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
