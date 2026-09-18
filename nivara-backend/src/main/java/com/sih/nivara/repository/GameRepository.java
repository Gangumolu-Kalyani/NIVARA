package com.sih.nivara.repository;

import com.sih.nivara.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link Game} catalogue entries (table games).
 */
@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
}
