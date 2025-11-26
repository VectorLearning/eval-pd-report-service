package com.evplus.report.repository;

import com.evplus.report.model.entity.ThresholdConfig;
import com.evplus.report.model.enums.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for ThresholdConfig entities.
 * Provides CRUD operations for report threshold configuration.
 */
@Repository
public interface ThresholdConfigRepository extends JpaRepository<ThresholdConfig, ReportType> {
    // No custom methods needed - use findById(ReportType) for lookups
}
