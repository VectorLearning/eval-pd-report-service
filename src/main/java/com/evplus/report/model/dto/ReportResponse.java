package com.evplus.report.model.dto;

import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for report generation responses.
 * Contains status, job information, and optionally the report data (for sync reports).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response from report generation request")
public class ReportResponse {

    /**
     * Type of report that was generated.
     */
    @Schema(description = "Type of report", example = "USER_ACTIVITY")
    private ReportType reportType;

    /**
     * Current status of the report.
     */
    @Schema(description = "Report status", example = "COMPLETED")
    private ReportStatus status;

    /**
     * Message providing additional context about the status.
     */
    @Schema(description = "Status message", example = "Report generated successfully")
    private String message;

    /**
     * Job ID for async reports (null for sync reports).
     */
    @Schema(description = "Job ID for tracking async reports", example = "550e8400-e29b-41d4-a716-446655440000")
    private String jobId;

    /**
     * Estimated completion time for async reports (null for sync reports).
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Estimated completion time for async reports", example = "2025-01-26T12:00:00")
    private LocalDateTime estimatedCompletionTime;

    /**
     * The generated report data (only for sync reports that completed successfully).
     */
    @Schema(description = "Generated report data (sync reports only)")
    private ReportData reportData;

    /**
     * Total number of records in the report.
     */
    @Schema(description = "Total number of records in the report", example = "1250")
    private Integer totalRecords;

    /**
     * Timestamp when the report was generated.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Schema(description = "Report generation timestamp", example = "2025-01-26T11:45:30")
    private LocalDateTime generatedAt;
}
