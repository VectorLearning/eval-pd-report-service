package com.evplus.report.repository;

import com.evplus.report.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for User entities.
 * Read-only repository for fetching user details for notifications.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    // No custom methods needed - findById from JpaRepository is sufficient
}
