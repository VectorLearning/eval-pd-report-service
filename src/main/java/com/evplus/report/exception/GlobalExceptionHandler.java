package com.evplus.report.exception;

import com.evplus.report.model.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global Exception Handler.
 *
 * Catches all exceptions thrown by controllers and converts them
 * to standardized ErrorResponse DTOs with appropriate HTTP status codes.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    /**
     * Handle ValidationException - 400 Bad Request.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        ValidationException ex,
        WebRequest request
    ) {
        logger.warn("Validation error: {}", ex.getMessage());

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            ex.getMessage(),
            request
        );

        if (ex.hasValidationErrors()) {
            errorResponse.setValidationErrors(ex.getValidationErrors());
        }

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle Spring's MethodArgumentNotValidException - 400 Bad Request.
     * Triggered by @Valid annotation on request bodies.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex,
        WebRequest request
    ) {
        logger.warn("Method argument validation failed: {}", ex.getMessage());

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Invalid request body",
            request
        );
        errorResponse.setFieldErrors(fieldErrors);

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle UnsupportedReportTypeException - 400 Bad Request.
     */
    @ExceptionHandler(UnsupportedReportTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedReportType(
        UnsupportedReportTypeException ex,
        WebRequest request
    ) {
        logger.warn("Unsupported report type: {}", ex.getReportType());

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            "UNSUPPORTED_REPORT_TYPE",
            ex.getMessage(),
            request
        );

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Handle UnauthorizedException - 403 Forbidden.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(
        UnauthorizedException ex,
        WebRequest request
    ) {
        logger.warn("Unauthorized access attempt: {}", ex.getMessage());

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "FORBIDDEN",
            ex.getMessage(),
            request
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle Spring Security AccessDeniedException - 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(
        AccessDeniedException ex,
        WebRequest request
    ) {
        logger.warn("Access denied: {}", ex.getMessage());

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "You don't have permission to access this resource",
            request
        );

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }

    /**
     * Handle ReportJobNotFoundException - 404 Not Found.
     */
    @ExceptionHandler(ReportJobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReportJobNotFound(
        ReportJobNotFoundException ex,
        WebRequest request
    ) {
        logger.warn("Report job not found: {}", ex.getJobId());

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.NOT_FOUND,
            "REPORT_JOB_NOT_FOUND",
            ex.getMessage(),
            request
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    /**
     * Handle ReportNotReadyException - 409 Conflict.
     */
    @ExceptionHandler(ReportNotReadyException.class)
    public ResponseEntity<ErrorResponse> handleReportNotReady(
        ReportNotReadyException ex,
        WebRequest request
    ) {
        logger.warn("Report not ready: jobId={}, status={}", ex.getJobId(), ex.getStatus());

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.CONFLICT,
            "REPORT_NOT_READY",
            ex.getMessage(),
            request
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }

    /**
     * Handle ReportGenerationException - 500 Internal Server Error.
     */
    @ExceptionHandler(ReportGenerationException.class)
    public ResponseEntity<ErrorResponse> handleReportGenerationException(
        ReportGenerationException ex,
        WebRequest request
    ) {
        logger.error("Report generation failed", ex);

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "REPORT_GENERATION_FAILED",
            "Failed to generate report. Please try again later.",
            request
        );

        // Include debug message in non-prod environments
        if (isDebugEnabled()) {
            errorResponse.setDebugMessage(ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle all other uncaught exceptions - 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
        Exception ex,
        WebRequest request
    ) {
        logger.error("Unexpected error occurred", ex);

        ErrorResponse errorResponse = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "An unexpected error occurred. Please contact support.",
            request
        );

        // Include debug message in non-prod environments
        if (isDebugEnabled()) {
            errorResponse.setDebugMessage(ex.getMessage());
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Build standardized error response.
     */
    private ErrorResponse buildErrorResponse(
        HttpStatus status,
        String error,
        String message,
        WebRequest request
    ) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();
        String correlationId = MDC.get("correlationId");

        return ErrorResponse.builder()
            .status(status.value())
            .error(error)
            .message(message)
            .path(path)
            .correlationId(correlationId)
            .timestamp(java.time.LocalDateTime.now())
            .build();
    }

    /**
     * Check if debug information should be included in error responses.
     */
    private boolean isDebugEnabled() {
        return "local".equals(activeProfile) || "dev".equals(activeProfile);
    }
}
