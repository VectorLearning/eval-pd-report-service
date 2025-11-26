package com.evplus.report.repository;

import com.evplus.report.model.entity.NotificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for NotificationEvent entities.
 * Write-only repository for creating notification events.
 * Used to integrate with existing teachpoint-web notification infrastructure.
 */
@Repository
public interface NotificationEventRepository extends JpaRepository<NotificationEvent, Integer> {
    // No custom methods needed - insert only operations
}
