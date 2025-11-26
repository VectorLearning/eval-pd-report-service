package com.evplus.report.model.entity;

import com.evplus.report.model.enums.ReportType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * JPA Entity for report threshold configuration.
 * Maps to the 'threshold_configs' table in the database.
 * Determines when to switch from synchronous to asynchronous report generation.
 */
@Entity
@Table(name = "threshold_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThresholdConfig {

    /**
     * Report type identifier (primary key).
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", length = 50, nullable = false)
    private ReportType reportType;

    /**
     * Maximum record count before switching to async processing.
     * Default: 5000 records (Apache POI can generate ~5K rows in 3-5 seconds).
     */
    @Column(name = "max_records", nullable = false)
    private Integer maxRecords = 5000;

    /**
     * Maximum estimated duration (in seconds) before switching to async.
     * Default: 10 seconds for good user experience.
     */
    @Column(name = "max_duration_seconds", nullable = false)
    private Integer maxDurationSeconds = 10;

    /**
     * Human-readable description of this threshold configuration.
     */
    @Column(name = "description")
    private String description;

    /**
     * Last update timestamp (auto-updated by database).
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * JPA lifecycle callback to set timestamp before insert.
     */
    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    /**
     * JPA lifecycle callback to update timestamp before update.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
