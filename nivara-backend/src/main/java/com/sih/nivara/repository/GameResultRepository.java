package com.sih.nivara.repository;

import com.sih.nivara.entity.GameResult;
import com.sih.nivara.entity.Patient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link GameResult} records (table game_results).
 *
 * <p>The finders fetch what their API response reads, because every association is lazy and
 * open-in-view is disabled: the single attempt brings its answers and each answer's subject,
 * the history list only the patient and game.
 */
@Repository
public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    /**
     * Looks an attempt up by its public uuid, with its answers. Backed by the unique constraint
     * uq_game_results_uuid (V3). Answers is the only collection fetched, so the join is safe.
     */
    @EntityGraph(attributePaths = {"patient", "game", "answers", "answers.memory", "answers.person",
            "answers.place", "answers.object"})
    Optional<GameResult> findByUuid(UUID uuid);

    /**
     * One patient's attempts, most recently started first, without answers. The ordering follows
     * index ix_game_results_patient_started, with the id breaking ties.
     */
    @EntityGraph(attributePaths = {"patient", "game"})
    List<GameResult> findByPatientOrderByStartedAtDescIdDesc(Patient patient);
}
