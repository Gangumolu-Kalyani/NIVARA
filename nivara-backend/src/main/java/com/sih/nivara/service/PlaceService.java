package com.sih.nivara.service;

import com.sih.nivara.dto.mapper.PlaceMapper;
import com.sih.nivara.dto.request.PlaceUpdateRequest;
import com.sih.nivara.entity.Patient;
import com.sih.nivara.entity.Place;
import com.sih.nivara.repository.PlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service access to {@link Place} records in a patient's personal memory.
 * Repository delegation, plus the transactional update its REST API needs. No NIVARA
 * business rules yet.
 * No delete method: this table soft-deletes through deleted_at, and its FKs are
 * ON DELETE RESTRICT, so removal is decided in a later phase.
 */
@Service
@Transactional(readOnly = true)
public class PlaceService {

    private final PlaceRepository placeRepository;

    public PlaceService(PlaceRepository placeRepository) {
        this.placeRepository = placeRepository;
    }

    /** All places, unfiltered. */
    public List<Place> findAll() {
        return placeRepository.findAll();
    }

    /** The place with this id, or empty when none exists. */
    public Optional<Place> findById(Long id) {
        return placeRepository.findById(id);
    }

    /**
     * The place with this public uuid, provided it belongs to this patient. Empty when it does
     * not exist or belongs to someone else, so callers cannot reach across patients.
     */
    public Optional<Place> findByUuidAndPatient(UUID uuid, Patient patient) {
        return placeRepository.findByUuidAndPatient(uuid, patient);
    }

    /** The place with this public uuid, whichever patient it belongs to, or empty. */
    public Optional<Place> findByUuid(UUID uuid) {
        return placeRepository.findByUuid(uuid);
    }

    /** One patient's places, by name. */
    public List<Place> findByPatient(Patient patient) {
        return placeRepository.findByPatientOrderByNameAscIdAsc(patient);
    }

    /**
     * Replaces the editable fields of an existing place, or reports empty when no place
     * has this uuid. Loading and changing it inside one transaction keeps the entity managed,
     * so the change is written by dirty checking and the patient fetched with it stays
     * initialised for the response. The patient itself cannot change.
     */
    @Transactional
    public Optional<Place> update(UUID uuid, PlaceUpdateRequest request) {
        return placeRepository.findByUuid(uuid).map(place -> {
            PlaceMapper.applyUpdate(request, place);
            return place;
        });
    }

    /** Inserts a new place or updates an existing one. */
    @Transactional
    public Place save(Place place) {
        return placeRepository.save(place);
    }
}
