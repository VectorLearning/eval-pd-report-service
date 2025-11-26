package com.evplus.report.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Abstract base class for all report data results.
 * Contains common fields that all reports should have.
 *
 * Concrete report data classes should:
 * 1. Extend this class
 * 2. Add report-specific data fields (e.g., list of records)
 * 3. Set totalRecords in their constructor/builder
 */
@Data
public abstract class ReportData {

    /**
     * Total number of records in the report.
     * Should be set by concrete implementations.
     */
    private Integer totalRecords;

    /**
     * Timestamp when the report was generated.
     * Automatically set to current time when created.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime generatedAt = LocalDateTime.now();
}
