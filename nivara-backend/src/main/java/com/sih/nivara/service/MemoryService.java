package com.sih.nivara.service;

import com.sih.nivara.entity.Memory;
import com.sih.nivara.repository.MemoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service access to {@link Memory} records in a patient's personal memory.
 * Phase 5B: repository delegation only. No NIVARA business rules yet.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class MemoryService {

    private final MemoryRepository memoryRepository;

    public MemoryService(MemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
    }

    /** All memories, unfiltered. */
    public List<Memory> findAll() {
        return memoryRepository.findAll();
    }

    /** The memory with this id, or empty when none exists. */
    public Optional<Memory> findById(Long id) {
        return memoryRepository.findById(id);
    }

    /** Inserts a new memory or updates an existing one. */
    @Transactional
    public Memory save(Memory memory) {
        return memoryRepository.save(memory);
    }
}
