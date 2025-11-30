package com.evplus.report.service;

import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.security.UserPrincipal;

/**
 * Service for integrating with teachpoint-web notification infrastructure.
 * Queues report completion notifications for email delivery.
 *
 * This service does NOT send emails directly. Instead, it:
 * - Inserts notification event to database
 * - Inserts notification queue record
 * - Sends SQS message to existing notification processor queue
 * - Existing teachpoint-web email processor handles actual email delivery
 */
public interface NotificationQueueService {

    /**
     * Queue a report completion notification for delivery.
     *
     * @param reportJob Completed report job with metadata
     * @param user User who should receive the notification
     * @param downloadUrl Presigned S3 URL for report download (7-day expiration)
     */
    void queueReportNotification(ReportJob reportJob, UserPrincipal user, String downloadUrl);
}
