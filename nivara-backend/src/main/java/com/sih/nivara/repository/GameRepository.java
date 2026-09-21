package com.sih.nivara.repository;

import com.sih.nivara.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * CRUD access to {@link Game} catalogue entries (table games).
 */
@Repository
public interface GameRepository extends JpaRepository<Game, Long> {

    /**
     * Looks a game up by the public key the API uses. Backed by the unique constraint
     * uq_games_code (V3); the code never changes once a game exists.
     */
    Optional<Game> findByCode(String code);

    /** The games currently offered to patients, by name. */
    List<Game> findByActiveTrueOrderByNameAsc();
}
