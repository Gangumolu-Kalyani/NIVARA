package com.sih.nivara.service;

import com.sih.nivara.entity.Game;
import com.sih.nivara.repository.GameRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link Game} catalogue entries.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 */
@Service
@Transactional(readOnly = true)
public class GameService {

    private final GameRepository gameRepository;

    public GameService(GameRepository gameRepository) {
        this.gameRepository = gameRepository;
    }

    /** All games, unfiltered. */
    public List<Game> findAll() {
        return gameRepository.findAll();
    }

    /** The game with this id, or empty when none exists. */
    public Optional<Game> findById(Long id) {
        return gameRepository.findById(id);
    }

    /** Inserts a new game or updates an existing one. */
    @Transactional
    public Game save(Game game) {
        return gameRepository.save(game);
    }

    /** Removes the game with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        gameRepository.deleteById(id);
    }
}
