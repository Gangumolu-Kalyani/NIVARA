package com.sih.nivara.game.service;

import com.sih.nivara.entity.Game;
import com.sih.nivara.game.dto.request.GenerateGameRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for getting the appropriate {@link GameGenerator} for a game code.
 *
 * <p>This factory uses Spring's dependency injection to collect all {@link GameGenerator}
 * implementations and route requests to the correct one based on the game code.
 */
@Component
public class GameGeneratorFactory {

    private final Map<String, GameGenerator> generatorsByCode;

    public GameGeneratorFactory(List<GameGenerator> generators) {
        this.generatorsByCode = generators.stream()
                .collect(Collectors.toMap(GameGenerator::getGameCode, Function.identity()));
    }

    /**
     * Gets the generator for the given game code.
     *
     * @param gameCode the game code (e.g., "MEMORY_MATCH")
     * @return the generator, or empty if no generator is registered for this code
     */
    public Optional<GameGenerator> getGenerator(String gameCode) {
        return Optional.ofNullable(generatorsByCode.get(gameCode));
    }

    /**
     * Gets the generator for the given game definition.
     *
     * @param game the game definition
     * @return the generator
     * @throws IllegalArgumentException if no generator is registered for this game
     */
    public GameGenerator getGenerator(Game game) {
        var gen = generatorsByCode.get(game.getCode());
        if (gen == null) {
            throw new IllegalArgumentException(
                    "No generator registered for game code: " + game.getCode());
        }
        return gen;
    }

    /**
     * Gets the generator for the given game request.
     *
     * @param request the generation request
     * @return the generator
     * @throws IllegalArgumentException if no generator is registered for this request's game code
     */
    public GameGenerator getGenerator(GenerateGameRequest request) {
        var gen = generatorsByCode.get(request.gameCode());
        if (gen == null) {
            throw new IllegalArgumentException(
                    "No generator registered for game code: " + request.gameCode());
        }
        return gen;
    }
}
