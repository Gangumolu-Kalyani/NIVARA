package com.sih.nivara.repository;

import com.sih.nivara.entity.GameResultAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link GameResultAnswer} records (table game_result_answers).
 */
@Repository
public interface GameResultAnswerRepository extends JpaRepository<GameResultAnswer, Long> {
}
