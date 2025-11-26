package com.evplus.report.model.enums;

/**
 * Enum representing the status of a report generation job.
 * Maps to integer values stored in the database for backward compatibility.
 */
public enum ReportStatus {
    /**
     * Report job has been queued for async processing.
     * Database value: 0
     */
    QUEUED(0),

    /**
     * Report job is currently being processed.
     * Database value: 1
     */
    PROCESSING(1),

    /**
     * Report job completed successfully.
     * Database value: 2
     */
    COMPLETED(2),

    /**
     * Report job failed with an error.
     * Database value: 3
     */
    FAILED(3);

    private final int value;

    ReportStatus(int value) {
        this.value = value;
    }

    /**
     * Get the integer value for database storage.
     * @return integer representation of the status
     */
    public int getValue() {
        return value;
    }

    /**
     * Convert integer value from database to enum.
     * @param value integer status code from database
     * @return corresponding ReportStatus enum
     * @throws IllegalArgumentException if value is not valid
     */
    public static ReportStatus fromValue(int value) {
        for (ReportStatus status : ReportStatus.values()) {
            if (status.value == value) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid ReportStatus value: " + value);
    }
}
