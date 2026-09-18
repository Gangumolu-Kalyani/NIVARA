package com.sih.nivara.repository;

import com.sih.nivara.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link Person} records in a patient's personal memory (table people).
 */
@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
}
