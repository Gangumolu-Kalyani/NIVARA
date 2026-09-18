package com.sih.nivara.repository;

import com.sih.nivara.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link Place} records in a patient's personal memory (table places).
 */
@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {
}
