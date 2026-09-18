package com.sih.nivara.repository;

import com.sih.nivara.entity.PersonalObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link PersonalObject} records in a patient's personal memory (table personal_objects).
 */
@Repository
public interface PersonalObjectRepository extends JpaRepository<PersonalObject, Long> {
}
