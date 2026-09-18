package com.sih.nivara.repository;

import com.sih.nivara.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * CRUD access to {@link AppUser} login accounts (table app_users).
 */
@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
}
