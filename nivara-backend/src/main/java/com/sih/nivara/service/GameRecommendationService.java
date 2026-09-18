package com.sih.nivara.service;

import com.sih.nivara.entity.GameRecommendation;
import com.sih.nivara.repository.GameRecommendationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link GameRecommendation} records.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 */
@Service
@Transactional(readOnly = true)
public class GameRecommendationService {

    private final GameRecommendationRepository gameRecommendationRepository;

    public GameRecommendationService(GameRecommendationRepository gameRecommendationRepository) {
        this.gameRecommendationRepository = gameRecommendationRepository;
    }

    /** All game recommendations, unfiltered. */
    public List<GameRecommendation> findAll() {
        return gameRecommendationRepository.findAll();
    }

    /** The game recommendation with this id, or empty when none exists. */
    public Optional<GameRecommendation> findById(Long id) {
        return gameRecommendationRepository.findById(id);
    }

    /** Inserts a new game recommendation or updates an existing one. */
    @Transactional
    public GameRecommendation save(GameRecommendation gameRecommendation) {
        return gameRecommendationRepository.save(gameRecommendation);
    }

    /** Removes the game recommendation with this id. Does nothing when no such row exists. */
    @Transactional
    public void deleteById(Long id) {
        gameRecommendationRepository.deleteById(id);
    }
}
