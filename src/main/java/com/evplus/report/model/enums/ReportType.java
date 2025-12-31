package com.evplus.report.model.enums;

/**
 * Enum representing different types of reports that can be generated.
 * Each report type corresponds to a specific report handler implementation.
 */
public enum ReportType {
    /**
     * User activity report - tracks user interactions, logins, course views, etc.
     */
    USER_ACTIVITY,

    /**
     * Activity by User report - Professional development activities for users.
     * Tracks PD events, online courses (Vector Training/Canvas), and credit hours earned.
     * Supports multiple data sources and customizable user/event properties.
     */
    ACTIVITY_BY_USER,

    /**
     * Dummy test report - used for testing async pipeline without real data dependencies.
     * This report type generates configurable test data for validating the complete
     * async workflow (SQS → Process → Excel → S3 → Notification).
     */
    DUMMY_TEST
}
