package com.evplus.report.repository;

import com.evplus.report.model.entity.NotificationQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for NotificationQueue entities.
 * Write-only repository for queueing notifications.
 * Used to integrate with existing teachpoint-web notification infrastructure.
 */
@Repository
public interface NotificationQueueRepository extends JpaRepository<NotificationQueue, Integer> {
    // No custom methods needed - insert only operations
}
