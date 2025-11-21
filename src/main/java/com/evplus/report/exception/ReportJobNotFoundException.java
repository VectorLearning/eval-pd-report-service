package com.evplus.report.exception;

/**
 * Exception thrown when a report job is not found by ID.
 *
 * Maps to HTTP 404 Not Found.
 */
public class ReportJobNotFoundException extends RuntimeException {

    private final String jobId;

    public ReportJobNotFoundException(String jobId) {
        super(String.format("Report job not found: %s", jobId));
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }
}
