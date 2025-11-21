package com.evplus.report.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standardized error response DTO.
 *
 * Returned by the GlobalExceptionHandler for all API errors.
 * Provides consistent error format across the application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard error response")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Error type/name", example = "VALIDATION_ERROR")
    private String error;

    @Schema(description = "Detailed error message", example = "Invalid report criteria provided")
    private String message;

    @Schema(description = "Request path that caused the error", example = "/reports/generate")
    private String path;

    @Schema(description = "Correlation ID for request tracing", example = "a1b2c3d4-e5f6-7890-ab12-cd34ef567890")
    private String correlationId;

    @Schema(description = "Timestamp when error occurred", example = "2025-01-20T10:30:45")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    @Schema(description = "Field-level validation errors (for 400 Bad Request)")
    private Map<String, String> fieldErrors;

    @Schema(description = "List of validation error messages")
    private List<String> validationErrors;

    @Schema(description = "Optional debug information (only in non-prod environments)")
    private String debugMessage;

    /**
     * Create basic error response with common fields.
     */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return ErrorResponse.builder()
            .status(status)
            .error(error)
            .message(message)
            .path(path)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
