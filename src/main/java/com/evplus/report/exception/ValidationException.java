package com.evplus.report.exception;

import java.util.ArrayList;
import java.util.List;

/**
 * Exception thrown when validation fails.
 *
 * Maps to HTTP 400 Bad Request.
 */
public class ValidationException extends RuntimeException {

    private final List<String> validationErrors;

    public ValidationException(String message) {
        super(message);
        this.validationErrors = new ArrayList<>();
    }

    public ValidationException(String message, List<String> validationErrors) {
        super(message);
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }

    public ValidationException(List<String> validationErrors) {
        super("Validation failed");
        this.validationErrors = validationErrors != null ? validationErrors : new ArrayList<>();
    }

    public List<String> getValidationErrors() {
        return validationErrors;
    }

    public boolean hasValidationErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }
}
