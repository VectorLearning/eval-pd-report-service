package com.evplus.report.exception;

/**
 * Exception thrown when report generation fails due to system error.
 *
 * Maps to HTTP 500 Internal Server Error.
 */
public class ReportGenerationException extends RuntimeException {

    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
