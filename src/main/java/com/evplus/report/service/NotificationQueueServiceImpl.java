package com.evplus.report.service;

import com.evplus.report.model.entity.NotificationEvent;
import com.evplus.report.model.entity.NotificationQueue;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.repository.NotificationEventRepository;
import com.evplus.report.repository.NotificationQueueRepository;
import com.evplus.report.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Production implementation of NotificationQueueService.
 * Integrates with existing teachpoint-web notification infrastructure.
 *
 * This implementation:
 * - Inserts to notification_events table
 * - Inserts to notification_queue table
 * - Sends SQS message to teachpoint-web notification processor
 *
 * Active for: dev, stage, prod profiles (excludes local)
 */
@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class NotificationQueueServiceImpl implements NotificationQueueService {

    private final NotificationEventRepository notificationEventRepository;
    private final NotificationQueueRepository notificationQueueRepository;
    private final SqsTemplate sqsTemplate;
    private final ObjectMapper objectMapper;

    @Value("${notification.queue.name}")
    private String notificationQueueName;

    @Value("${notification.event-type:REPORT_READY_FOR_DOWNLOAD}")
    private String eventType;

    @Value("${notification.level:IMMEDIATELY}")
    private String notificationLevel;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Queue a report completion notification.
     * CRITICAL: This method must NOT throw exceptions - notification failure should not fail the job.
     *
     * @param reportJob Completed report job
     * @param user User who should receive notification
     * @param downloadUrl Presigned S3 URL for download
     */
    @Override
    @Transactional
    public void queueReportNotification(ReportJob reportJob, UserPrincipal user, String downloadUrl) {
        try {
            log.info("Queuing report notification: jobId={}, userId={}, email={}",
                reportJob.getReportId(), user.getUserId(), user.getEmail());

            // Step 1: Create and save notification event
            NotificationEvent event = createNotificationEvent(reportJob, user, downloadUrl);
            NotificationEvent savedEvent = notificationEventRepository.save(event);
            log.debug("Notification event saved: eventId={}", savedEvent.getId());

            // Step 2: Create and save notification queue record
            NotificationQueue queue = createNotificationQueue(reportJob, savedEvent);
            NotificationQueue savedQueue = notificationQueueRepository.save(queue);
            log.debug("Notification queue record saved: queueId={}", savedQueue.getId());

            // Step 3: Send SQS message to notification processor
            sendSqsNotification(savedQueue, savedEvent, reportJob);

            log.info("Successfully queued report notification: jobId={}, queueId={}",
                reportJob.getReportId(), savedQueue.getId());

        } catch (Exception e) {
            // CRITICAL: Do NOT throw exception - log error but allow job to complete
            log.error("Failed to queue report notification: jobId={}, userId={}, email={}. " +
                "User can still download via /reports/download/{} endpoint.",
                reportJob.getReportId(), user.getUserId(), user.getEmail(), reportJob.getReportId(), e);
        }
    }

    /**
     * Create notification event entity.
     *
     * @param reportJob The completed report job
     * @param user The user who should receive notification
     * @param downloadUrl Presigned S3 URL for download
     * @return Notification event ready to be saved
     */
    private NotificationEvent createNotificationEvent(ReportJob reportJob, UserPrincipal user,
                                                     String downloadUrl) throws JsonProcessingException {
        NotificationEvent event = new NotificationEvent();
        event.setDistrictId(reportJob.getDistrictId());
        event.setEvent(eventType);
        event.setDate(LocalDateTime.now());
        event.setUserId(user.getUserId());
        event.setObjectStr(reportJob.getReportId()); // Store report job ID

        // Build message JSON with report details
        Map<String, String> messageData = new HashMap<>();
        messageData.put("reportName", getReportDisplayName(reportJob));
        messageData.put("downloadUrl", downloadUrl);
        messageData.put("expirationDate", calculateExpirationDate());
        messageData.put("reportType", reportJob.getReportType().name());
        messageData.put("firstName", user.getFirstName() != null ? user.getFirstName() : user.getUsername());

        String messageJson = objectMapper.writeValueAsString(messageData);
        event.setMessage(messageJson);

        event.setNeedAttachments(false);

        return event;
    }

    /**
     * Create notification queue entity.
     *
     * @param reportJob The completed report job
     * @param event The saved notification event
     * @return Notification queue record ready to be saved
     */
    private NotificationQueue createNotificationQueue(ReportJob reportJob, NotificationEvent event) {
        NotificationQueue queue = new NotificationQueue();
        queue.setDistrictId(reportJob.getDistrictId());
        queue.setLevel(notificationLevel); // "IMMEDIATELY" for urgent notifications
        queue.setNotificationEventId(event.getId());
        queue.setSqsQueued(true); // Indicates message will be sent to SQS

        return queue;
    }

    /**
     * Send SQS message to notification processor queue.
     *
     * @param queue The saved notification queue record
     * @param event The saved notification event
     * @param reportJob The completed report job
     */
    private void sendSqsNotification(NotificationQueue queue, NotificationEvent event,
                                    ReportJob reportJob) throws JsonProcessingException {
        // Build SQS message body
        Map<String, Object> sqsMessage = new HashMap<>();
        sqsMessage.put("notificationQueueId", queue.getId());
        sqsMessage.put("notificationEventId", event.getId());
        sqsMessage.put("districtId", reportJob.getDistrictId());
        sqsMessage.put("level", notificationLevel);

        String messageJson = objectMapper.writeValueAsString(sqsMessage);

        // Send to notification processor queue
        sqsTemplate.send(notificationQueueName, messageJson);

        log.info("Sent SQS notification message: queue={}, messageId={}, queueId={}",
            notificationQueueName, queue.getId(), queue.getId());
    }

    /**
     * Get user-friendly report display name.
     *
     * @param reportJob The report job
     * @return Formatted report name for display
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
     *
     * @return Formatted expiration date string
     */
    private String calculateExpirationDate() {
        return LocalDateTime.now().plusDays(7).format(DATE_FORMATTER);
    }
}
