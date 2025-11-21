package com.evplus.report.exception;

/**
 * Exception thrown when attempting to download a report that's not ready yet.
 *
 * Maps to HTTP 409 Conflict.
 */
public class ReportNotReadyException extends RuntimeException {

    private final String jobId;
    private final String status;

    public ReportNotReadyException(String jobId, String status) {
        super(String.format("Report %s is not ready yet. Current status: %s", jobId, status));
        this.jobId = jobId;
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public String getStatus() {
        return status;
    }
}
