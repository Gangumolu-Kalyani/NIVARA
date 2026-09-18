package com.sih.nivara.repository;

import com.sih.nivara.entity.GameRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link GameRecommendation} records (table game_recommendations).
 */
@Repository
public interface GameRecommendationRepository extends JpaRepository<GameRecommendation, Long> {
}
