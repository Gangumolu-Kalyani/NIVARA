package com.sih.nivara.game.service.generator.memorytimeline;

import com.sih.nivara.entity.Game;
import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.game.dto.request.GenerateGameRequest;
import com.sih.nivara.game.dto.response.GameContentReference;
import com.sih.nivara.game.dto.response.GameHint;
import com.sih.nivara.game.dto.response.GeneratedGameResponse;
import com.sih.nivara.game.dto.response.GeneratedQuestion;
import com.sih.nivara.game.dto.response.Option;
import com.sih.nivara.game.service.GameGenerator;
import com.sih.nivara.service.MemoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Game generator for Memory Timeline - chronological ordering of personal events.
 *
 * <p>This generator creates a timeline game where the patient arranges memories/events
 * in chronological order. The game uses the patient's memories data, sorted by date.
 */
@Service
public class MemoryTimelineGenerator implements GameGenerator {

    private final MemoryService memoryService;

    public MemoryTimelineGenerator(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Override
    public String getGameCode() {
        return "MEMORY_TIMELINE";
    }

    @Override
    public boolean canGenerate(Patient patient, short difficulty) {
        // Get memories for the patient
        var memories = memoryService.findByPatient(patient);
        
        // Filter to only memories marked for games
        long count = memories.stream()
                .filter(Memory::isIncludeInGames)
                .count();
        
        // Need at least 2 memories for difficulty 1, more for higher difficulties
        int minMemories = Math.min(5, (difficulty == 1) ? 2 : (difficulty == 2) ? 3 : 4);
        return count >= minMemories;
    }

    @Override
    public GeneratedGameResponse generate(Patient patient, GenerateGameRequest request, Game game) {
        // Get all memories for the patient
        var allMemories = memoryService.findByPatient(patient);
        
        // Filter to only memories marked for games
        var memories = allMemories.stream()
                .filter(Memory::isIncludeInGames)
                .toList();
        
        // Sort by date (ascending - oldest first)
        memories.sort(Comparator.comparing(Memory::getOccurredOn));
        
        // Determine number of events based on difficulty
        int numEvents = getEventsForDifficulty(request.difficulty());
        
        // Select memories for the game (up to numEvents)
        numEvents = Math.min(numEvents, memories.size());
        var selectedMemories = memories.subList(0, numEvents);
        
        // Create questions - each question asks patient to arrange events
        var questions = createTimelineQuestions(selectedMemories, request.languageCode());
        
        // Create content references
        var contentReferences = createContentReferences(selectedMemories);
        
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
                        2,  // min events
                        5   // max events
                )
        );
    }
    
    /**
     * Gets the number of events for a given difficulty level.
     */
    private int getEventsForDifficulty(short difficulty) {
        return switch (difficulty) {
            case 1 -> 2;   // 2 photos
            case 2 -> 3;   // 3 photos
            case 3 -> 4;   // 4 photos
            case 4 -> 5;   // 5 photos
            case 5 -> 5;   // 5 photos (max)
            default -> 2;
        };
    }
    
    /**
     * Creates timeline questions for the selected memories.
     * 
     * <p>For simplicity, we create one question that asks the patient to arrange
     * all selected memories in chronological order.
     */
    private List<GeneratedQuestion> createTimelineQuestions(List<Memory> memories, String languageCode) {
        var questions = new ArrayList<GeneratedQuestion>();
        
        // Question 1: Arrange all memories in chronological order
        var question = createArrangementQuestion((short) 1, memories, languageCode);
        questions.add(question);
        
        return questions;
    }
    
    /**
     * Creates an arrangement question for chronological ordering.
     */
    private GeneratedQuestion createArrangementQuestion(short questionNumber, List<Memory> memories, String languageCode) {
        var questionText = "Arrange these events in the order they happened (oldest first):";
        
        // Create options with memories in random order (for the game to reorder)
        var options = createRandomizedOptions(memories);
        
        // Create hint
        var hint = GameHint.of(1, "The first event happened longest ago.", com.sih.nivara.game.dto.response.HintType.TEXT);
        
        return new GeneratedQuestion(
                questionNumber,
                "TIMELINE_ORDER",
                questionText,
                languageCode,
                hint,
                options
        );
    }
    
    /**
     * Creates options with memories in randomized order.
     * In a real implementation, the frontend would handle reordering.
     * For now, we return them sorted and let the frontend randomize for display.
     */
    private List<Option> createRandomizedOptions(List<Memory> memories) {
        var options = new ArrayList<Option>();
        
        for (var memory : memories) {
            options.add(new Option("MEMORY", memory.getTitle()));
        }
        
        return options;
    }
    
    /**
     * Creates content references for the memories.
     */
    private List<GameContentReference> createContentReferences(List<Memory> memories) {
        var references = new ArrayList<GameContentReference>();
        
        for (var memory : memories) {
            var extraInfo = getMemoryTypeInfo(memory);
            references.add(new GameContentReference(
                    "MEMORY",
                    memory.getUuid().toString(),
                    memory.getTitle(),
                    extraInfo
            ));
        }
        
        return references;
    }
    
    /**
     * Gets a human-readable memory type string.
     */
    private String getMemoryTypeInfo(com.sih.nivara.entity.Memory memory) {
        if (memory.getMemoryType() == null) {
            return "Event";
        }
        return memory.getMemoryType().name().replace("_", " ");
    }
}
