package com.sih.nivara.repository;

import com.sih.nivara.entity.Memory;
import com.sih.nivara.entity.Patient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRUD access to {@link Memory} records in a patient's personal memory (table memories).
 *
 * <p>Both finders fetch the patient, place, people and objects with the memory. Those
 * associations are lazy and open-in-view is disabled, so a caller that maps a memory after the
 * transaction has closed would otherwise fail. The two collections are Sets, which is what
 * makes fetching both in one query safe.
 */
@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {

    /**
     * Looks a memory up by the public identifier the API uses. Backed by the unique constraint
     * uq_memories_uuid (V2), so it matches at most one row.
     */
    @EntityGraph(attributePaths = {"patient", "place", "people", "objects"})
    Optional<Memory> findByUuid(UUID uuid);

    /**
     * One patient's memories, most recent first. The ordering follows index
     * ix_memories_patient_occurred_on, with the id breaking ties within a day.
     */
    @EntityGraph(attributePaths = {"patient", "place", "people", "objects"})
    List<Memory> findByPatientOrderByOccurredOnDescIdDesc(Patient patient);
}
