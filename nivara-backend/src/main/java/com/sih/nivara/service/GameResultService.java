package com.sih.nivara.service;

import com.sih.nivara.entity.GameResult;
import com.sih.nivara.repository.GameResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link GameResult} records.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 */
@Service
@Transactional(readOnly = true)
public class GameResultService {

    private final GameResultRepository gameResultRepository;

    public GameResultService(GameResultRepository gameResultRepository) {
        this.gameResultRepository = gameResultRepository;
    }

    /** All game results, unfiltered. */
    public List<GameResult> findAll() {
        return gameResultRepository.findAll();
    }

    /** The game result with this id, or empty when none exists. */
    public Optional<GameResult> findById(Long id) {
        return gameResultRepository.findById(id);
    }

    /** Inserts a new game result or updates an existing one. */
    @Transactional
    public GameResult save(GameResult gameResult) {
        return gameResultRepository.save(gameResult);
    }

    /** Removes the game result with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        gameResultRepository.deleteById(id);
    }
}
