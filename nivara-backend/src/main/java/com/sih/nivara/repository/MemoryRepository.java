package com.sih.nivara.repository;

import com.sih.nivara.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link Memory} records in a patient's personal memory (table memories).
 */
@Repository
public interface MemoryRepository extends JpaRepository<Memory, Long> {
}
