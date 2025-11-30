package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.exception.ReportJobNotFoundException;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.entity.User;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.repository.UserRepository;
import com.evplus.report.security.UserPrincipal;
import com.evplus.report.service.handler.HandlerRegistry;
import com.evplus.report.service.handler.ReportHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Async Report Processor - SQS Listener for background report generation.
 *
 * This service:
 * - Listens to the SQS report queue for new report jobs
 * - Implements idempotency to handle duplicate messages (Standard SQS)
 * - Orchestrates the complete report generation pipeline
 * - Updates job status and handles errors
 *
 * Processing Flow:
 * 1. Receive SQS message with jobId
 * 2. Load job from database and check idempotency
 * 3. Deserialize criteria and call appropriate handler
 * 4. Generate Excel using ExcelReportGenerator
 * 5. Upload to S3 and get presigned URL
 * 6. Update job status to COMPLETED
 * 7. Queue notification for user (Task 4.4)
 *
 * Error Handling:
 * - Failed jobs are marked as FAILED with error message
 * - Exceptions are logged and re-thrown to trigger SQS retry
 * - MDC correlationId is used for request tracing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncReportProcessor {

    private final ReportJobRepository reportJobRepository;
    private final HandlerRegistry handlerRegistry;
    private final ExcelReportGenerator excelReportGenerator;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final NotificationQueueService notificationQueueService;
    private final UserRepository userRepository;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    private static final Duration PRESIGNED_URL_EXPIRATION = Duration.ofDays(7);

    /**
     * SQS Listener method - processes report generation jobs.
     *
     * This method is automatically invoked when a message arrives in the SQS queue.
     * It implements idempotency checks to prevent duplicate processing.
     *
     * @param message SQS message containing the jobId
     */
    @SqsListener("${aws.sqs.queue-name}")
    public void processReportJob(Message<String> message) {
        String jobId = null;

        try {
            // Extract jobId from message body
            jobId = message.getPayload();
            log.info("Received SQS message for report job: {}", jobId);

            // Set MDC for correlation tracking
            MDC.put("correlationId", jobId);
            MDC.put("jobId", jobId);

            // Process the job
            processJob(jobId);

            log.info("Successfully completed report job: {}", jobId);

        } catch (Exception e) {
            log.error("Failed to process report job: {}", jobId, e);

            // Handle job failure (update status to FAILED)
            if (jobId != null) {
                handleJobFailure(jobId, e);
            }

            // Re-throw to trigger SQS retry mechanism
            throw new ReportGenerationException(
                String.format("Failed to process report job: %s", jobId), e
            );

        } finally {
            // Clear MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Process a single report job.
     *
     * Steps:
     * 1. Load job from database with idempotency check
     * 2. Deserialize criteria
     * 3. Generate report data
     * 4. Create Excel file
     * 5. Upload to S3
     * 6. Generate presigned URL
     * 7. Update job status to COMPLETED
     *
     * @param jobId the job ID to process
     */
    @Transactional
    protected void processJob(String jobId) {
        // Step 1: Load job from database
        ReportJob job = reportJobRepository.findById(jobId)
            .orElseThrow(() -> new ReportJobNotFoundException(jobId));

        // Step 2: Idempotency check - Skip if already processed
        if (job.getStatus() == ReportStatus.COMPLETED) {
            log.info("Job {} already completed, skipping duplicate message", jobId);
            return;
        }

        if (job.getStatus() == ReportStatus.PROCESSING) {
            log.warn("Job {} already being processed, skipping duplicate message", jobId);
            return;
        }

        // Step 3: Update status to PROCESSING
        job.setStatus(ReportStatus.PROCESSING);
        job.setStartedDate(LocalDateTime.now());
        reportJobRepository.save(job);
        log.info("Started processing job: {} (type: {})", jobId, job.getReportType());

        try {
            // Step 4: Deserialize criteria from JSON
            ReportHandler handler = handlerRegistry.getHandler(job.getReportType());
            Class<? extends ReportCriteria> criteriaClass = handler.getCriteriaClass();
            ReportCriteria criteria = objectMapper.readValue(job.getReportParams(), criteriaClass);
            log.debug("Deserialized criteria for job: {}", jobId);

            // Step 5: Generate report data
            long startTime = System.currentTimeMillis();
            ReportData reportData = handler.generateReport(criteria);
            long generationTime = System.currentTimeMillis() - startTime;
            log.info("Generated report data for job {}: {} records in {} ms",
                jobId, reportData.getTotalRecords(), generationTime);

            // Step 6: Create Excel file
            startTime = System.currentTimeMillis();
            byte[] excelBytes = excelReportGenerator.generateExcel(reportData);
            long excelTime = System.currentTimeMillis() - startTime;
            log.info("Generated Excel for job {}: {} bytes in {} ms",
                jobId, excelBytes.length, excelTime);

            // Step 7: Upload to S3
            String filename = String.format("%s_%s.xlsx",
                job.getReportType().name(),
                jobId);
            startTime = System.currentTimeMillis();
            String s3Key = s3Service.uploadReport(
                job.getDistrictId(),
                jobId,
                excelBytes,
                filename
            );
            long uploadTime = System.currentTimeMillis() - startTime;
            log.info("Uploaded report to S3 for job {}: key={} in {} ms",
                jobId, s3Key, uploadTime);

            // Step 8: Generate presigned URL (7-day expiration)
            String presignedUrl = s3Service.generatePresignedUrl(s3Key, PRESIGNED_URL_EXPIRATION);
            log.info("Generated presigned URL for job {}: valid for {} days", jobId, PRESIGNED_URL_EXPIRATION.toDays());

            // Step 9: Update job status to COMPLETED
            job.setStatus(ReportStatus.COMPLETED);
            job.setCompletedDate(LocalDateTime.now());
            job.setS3Url(presignedUrl);
            job.setFilename(filename);
            job.setErrorMessage(null);  // Clear any previous error from failed attempts
            reportJobRepository.save(job);

            log.info("Successfully completed job {}: s3Key={}, presignedUrl={}, filename={}",
                jobId, s3Key, maskUrl(presignedUrl), filename);

            // Step 10: Queue notification for user
            queueNotificationForUser(job, presignedUrl);

        } catch (Exception e) {
            log.error("Error processing job {}: {}", jobId, e.getMessage(), e);
            throw new ReportGenerationException(
                String.format("Failed to process job %s: %s", jobId, e.getMessage()), e
            );
        }
    }

    /**
     * Handle job failure by updating status to FAILED.
     *
     * This method ensures the job status is updated even if the main processing fails.
     * Defensive programming: catches all exceptions to prevent failure cascade.
     *
     * @param jobId the job ID that failed
     * @param error the exception that caused the failure
     */
    private void handleJobFailure(String jobId, Exception error) {
        try {
            log.warn("Handling failure for job: {}", jobId);

            ReportJob job = reportJobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.error("Cannot update failure status - job not found: {}", jobId);
                return;
            }

            // Update job status to FAILED
            job.setStatus(ReportStatus.FAILED);
            job.setCompletedDate(LocalDateTime.now());
            job.setErrorMessage(truncateErrorMessage(error.getMessage()));
            reportJobRepository.save(job);

            log.info("Updated job {} to FAILED status: {}", jobId, job.getErrorMessage());

        } catch (Exception e) {
            // Defensive: log error but don't re-throw to prevent failure cascade
            log.error("Failed to update job failure status for job: {}", jobId, e);
        }
    }

    /**
     * Truncate error message to fit database column size (TEXT type).
     * Prevents database errors from overly long error messages.
     */
    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null) {
            return "Unknown error";
        }

        final int MAX_LENGTH = 1000;
        if (errorMessage.length() > MAX_LENGTH) {
            return errorMessage.substring(0, MAX_LENGTH) + "... (truncated)";
        }

        return errorMessage;
    }

    /**
     * Mask presigned URL for logging (hide sensitive query parameters).
     */
    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }

        int queryIndex = url.indexOf('?');
        return queryIndex > 0 ? url.substring(0, queryIndex) + "?[MASKED]" : url;
    }

    /**
     * Queue notification for user when report is ready.
     * Fetches user details from database and delegates to NotificationQueueService.
     *
     * @param job Completed report job
     * @param presignedUrl Presigned S3 URL for download
     */
    private void queueNotificationForUser(ReportJob job, String presignedUrl) {
        try {
            // Fetch user details from database
            User user = userRepository.findById(job.getUserId()).orElse(null);
            if (user == null) {
                log.warn("Cannot queue notification - user not found: userId={}", job.getUserId());
                return;
            }

            // Build UserPrincipal from User entity
            UserPrincipal userPrincipal = UserPrincipal.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();

            // Queue notification via NotificationQueueService
            notificationQueueService.queueReportNotification(job, userPrincipal, presignedUrl);

            log.info("Queued notification for user: jobId={}, userId={}, email={}",
                job.getReportId(), user.getUserId(), user.getEmail());

        } catch (Exception e) {
            // CRITICAL: Do NOT throw exception - notification failure should not fail the job
            log.error("Failed to queue notification for job {}: {}. User can still download via /reports/download/{} endpoint",
                job.getReportId(), e.getMessage(), job.getReportId(), e);
        }
    }
}
