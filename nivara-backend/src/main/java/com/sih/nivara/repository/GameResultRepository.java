package com.sih.nivara.repository;

import com.sih.nivara.entity.GameResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link GameResult} records (table game_results).
 */
@Repository
public interface GameResultRepository extends JpaRepository<GameResult, Long> {
}
