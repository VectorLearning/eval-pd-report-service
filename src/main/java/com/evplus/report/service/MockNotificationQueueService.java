package com.evplus.report.service;

import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.security.UserPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Mock implementation of NotificationQueueService for local development.
 *
 * This implementation:
 * - Logs notification details to console instead of database inserts
 * - Does NOT send actual SQS messages
 * - Simulates notification queueing for testing purposes
 *
 * Active for: local profile only
 */
@Service
@Profile("local")
@Slf4j
public class MockNotificationQueueService implements NotificationQueueService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    public void queueReportNotification(ReportJob reportJob, UserPrincipal user, String downloadUrl) {
        log.info("========================================");
        log.info("MOCK NOTIFICATION QUEUE SERVICE (Local Development)");
        log.info("========================================");
        log.info("Report Notification Details:");
        log.info("  - Job ID: {}", reportJob.getReportId());
        log.info("  - Report Type: {}", reportJob.getReportType());
        log.info("  - District ID: {}", reportJob.getDistrictId());
        log.info("");
        log.info("Recipient Details:");
        log.info("  - User ID: {}", user.getUserId());
        log.info("  - Email: {}", user.getEmail());
        log.info("  - Name: {} {}", user.getFirstName(), user.getLastName());
        log.info("");
        log.info("Download Details:");
        log.info("  - Download URL: {}", maskUrl(downloadUrl));
        log.info("  - Expiration Date: {}", calculateExpirationDate());
        log.info("");
        log.info("Notification Event Data:");
        log.info("  - Event Type: REPORT_READY_FOR_DOWNLOAD");
        log.info("  - Level: IMMEDIATELY");
        log.info("  - Report Name: {}", getReportDisplayName(reportJob));
        log.info("");
        log.info("NOTE: In production, this would:");
        log.info("  1. Insert notification_event record");
        log.info("  2. Insert notification_queue record");
        log.info("  3. Send SQS message to notification processor");
        log.info("  4. Teachpoint-web would send email via AWS SES");
        log.info("========================================");
    }

    /**
     * Get user-friendly report display name.
     */
    private String getReportDisplayName(ReportJob reportJob) {
        return switch (reportJob.getReportType()) {
            case USER_ACTIVITY -> "User Activity Report";
            case DUMMY_TEST -> "Test Report";
            default -> reportJob.getReportType().name().replace("_", " ") + " Report";
        };
    }

    /**
     * Calculate expiration date (7 days from now).
     */
    private String calculateExpirationDate() {
        return LocalDateTime.now().plusDays(7).format(DATE_FORMATTER);
    }

    /**
     * Mask sensitive parts of URL for logging.
     */
    private String maskUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        // Mask query parameters to avoid logging presigned signatures
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            return url.substring(0, queryIndex) + "?[MASKED]";
        }
        return url;
    }
}
