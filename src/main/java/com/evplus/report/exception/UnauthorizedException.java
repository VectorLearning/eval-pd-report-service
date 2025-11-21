package com.evplus.report.exception;

/**
 * Exception thrown when user attempts to access a resource they don't own.
 *
 * Maps to HTTP 403 Forbidden.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException() {
        super("You are not authorized to access this resource");
    }
}
