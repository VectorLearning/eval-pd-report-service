package com.evplus.report.model.dto;

import com.evplus.report.model.enums.ReportType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DTO for report generation requests.
 * Contains the report type and polymorphic criteria.
 */
@Data
@Schema(description = "Request to generate a report")
public class ReportRequest {

    /**
     * Type of report to generate.
     */
    @NotNull(message = "Report type is required")
    @Schema(description = "Type of report to generate", example = "USER_ACTIVITY", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReportType reportType;

    /**
     * Report-specific criteria.
     * The actual type depends on reportType (polymorphic).
     */
    @NotNull(message = "Report criteria is required")
    @Schema(description = "Report-specific criteria (varies by report type)", requiredMode = Schema.RequiredMode.REQUIRED)
    private ReportCriteria criteria;
}
