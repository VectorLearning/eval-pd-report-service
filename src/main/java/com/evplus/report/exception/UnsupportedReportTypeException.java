package com.evplus.report.exception;

/**
 * Exception thrown when an unknown/unsupported report type is requested.
 *
 * Maps to HTTP 400 Bad Request.
 */
public class UnsupportedReportTypeException extends RuntimeException {

    private final String reportType;

    public UnsupportedReportTypeException(String reportType) {
        super(String.format("Unsupported report type: %s", reportType));
        this.reportType = reportType;
    }

    public String getReportType() {
        return reportType;
    }
}
