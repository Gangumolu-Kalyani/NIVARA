package com.sih.nivara.service;

import com.sih.nivara.entity.GameResultAnswer;
import com.sih.nivara.repository.GameResultAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link GameResultAnswer} records.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 */
@Service
@Transactional(readOnly = true)
public class GameResultAnswerService {

    private final GameResultAnswerRepository gameResultAnswerRepository;

    public GameResultAnswerService(GameResultAnswerRepository gameResultAnswerRepository) {
        this.gameResultAnswerRepository = gameResultAnswerRepository;
    }

    /** All game result answers, unfiltered. */
    public List<GameResultAnswer> findAll() {
        return gameResultAnswerRepository.findAll();
    }

    /** The game result answer with this id, or empty when none exists. */
    public Optional<GameResultAnswer> findById(Long id) {
        return gameResultAnswerRepository.findById(id);
    }

    /** Inserts a new game result answer or updates an existing one. */
    @Transactional
    public GameResultAnswer save(GameResultAnswer gameResultAnswer) {
        return gameResultAnswerRepository.save(gameResultAnswer);
    }

    /** Removes the game result answer with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        gameResultAnswerRepository.deleteById(id);
    }
}
