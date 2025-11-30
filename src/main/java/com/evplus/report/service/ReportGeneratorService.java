package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.dto.ReportResponse;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.service.handler.HandlerRegistry;
import com.evplus.report.service.handler.ReportHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Central orchestrator service for report generation.
 * Coordinates the entire report generation flow:
 * 1. Validates request
 * 2. Determines sync vs async processing
 * 3. Routes to appropriate processing path
 *
 * This is the main entry point for all report generation requests.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGeneratorService {

    private final HandlerRegistry handlerRegistry;
    private final ThresholdService thresholdService;
    private final ReportJobRepository reportJobRepository;
    private final ObjectMapper objectMapper;
    private final SqsTemplate sqsTemplate;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    /**
     * Generate a report based on the request.
     * This is the main public API for report generation.
     *
     * @param request the report request with type and criteria
     * @param userId the ID of the user requesting the report
     * @param districtId the ID of the district for authorization
     * @return report response with status and data/jobId
     * @throws ValidationException if request validation fails
     * @throws ReportGenerationException if report generation fails
     */
    @Transactional
    public ReportResponse generateReport(ReportRequest request, Integer userId, Integer districtId) {
        log.info("Generating report: type={}, userId={}, districtId={}",
            request.getReportType(), userId, districtId);

        // Step 1: Get the appropriate handler
        ReportType reportType = request.getReportType();
        ReportHandler handler = handlerRegistry.getHandler(reportType);

        // Step 2: Validate the criteria
        handler.validateCriteria(request.getCriteria());
        log.debug("Criteria validation passed for report type: {}", reportType);

        // Step 3: Check if async processing is required
        boolean requiresAsync = handler.exceedsAsyncThreshold(request.getCriteria());

        // Step 4: Route to appropriate processing path
        if (requiresAsync) {
            log.info("Report exceeds threshold, processing asynchronously");
            return processAsyncReport(request, handler, userId, districtId);
        } else {
            log.info("Report within threshold, processing synchronously");
            return processSyncReport(request, handler, userId, districtId);
        }
    }

    /**
     * Process a report synchronously.
     * Generates the report immediately and returns the data in the response.
     *
     * @param request the report request
     * @param handler the report handler
     * @param userId user ID
     * @param districtId district ID
     * @return report response with status COMPLETED and report data
     * @throws ReportGenerationException if generation fails
     */
    private ReportResponse processSyncReport(ReportRequest request, ReportHandler handler,
                                            Integer userId, Integer districtId) {
        try {
            LocalDateTime startTime = LocalDateTime.now();

            // Generate the report
            ReportData reportData = handler.generateReport(request.getCriteria());

            LocalDateTime endTime = LocalDateTime.now();
            log.info("Sync report generated successfully in {} ms",
                java.time.Duration.between(startTime, endTime).toMillis());

            // Build response
            return ReportResponse.builder()
                .reportType(request.getReportType())
                .status(ReportStatus.COMPLETED)
                .message("Report generated successfully")
                .reportData(reportData)
                .totalRecords(reportData.getTotalRecords())
                .generatedAt(reportData.getGeneratedAt())
                .build();

        } catch (Exception e) {
            log.error("Sync report generation failed: type={}, userId={}",
                request.getReportType(), userId, e);
            throw new ReportGenerationException(
                String.format("Failed to generate %s report: %s",
                    request.getReportType(), e.getMessage()),
                e
            );
        }
    }

    /**
     * Process a report asynchronously.
     * Creates a job record and queues it for background processing.
     *
     * @param request the report request
     * @param handler the report handler
     * @param userId user ID
     * @param districtId district ID
     * @return report response with status QUEUED and job ID
     * @throws ReportGenerationException if job creation fails
     */
    private ReportResponse processAsyncReport(ReportRequest request, ReportHandler handler,
                                             Integer userId, Integer districtId) {
        try {
            // Generate unique job ID
            String jobId = UUID.randomUUID().toString();

            // Serialize criteria to JSON
            String criteriaJson = objectMapper.writeValueAsString(request.getCriteria());

            // Create job record
            ReportJob job = new ReportJob();
            job.setReportId(jobId);
            job.setDistrictId(districtId);
            job.setUserId(userId);
            job.setReportType(request.getReportType());
            job.setReportParams(criteriaJson);
            job.setStatus(ReportStatus.QUEUED);
            job.setRequestedDate(LocalDateTime.now());

            // Save to database
            reportJobRepository.save(job);
            log.info("Created async report job: jobId={}, type={}", jobId, request.getReportType());

            // Send SQS message AFTER transaction commits to avoid race condition
            // This ensures the job is visible in the database before the listener picks it up
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sqsTemplate.send(queueName, jobId);
                    log.info("Sent SQS message for job: {} to queue: {}", jobId, queueName);
                }
            });

            // Estimate completion time (5 minutes from now)
            LocalDateTime estimatedCompletion = LocalDateTime.now().plusMinutes(5);

            // Build response
            return ReportResponse.builder()
                .reportType(request.getReportType())
                .status(ReportStatus.QUEUED)
                .message("Report queued for processing")
                .jobId(jobId)
                .estimatedCompletionTime(estimatedCompletion)
                .build();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize criteria to JSON: type={}",
                request.getReportType(), e);
            throw new ReportGenerationException("Failed to queue report: Invalid criteria", e);
        } catch (Exception e) {
            log.error("Failed to create async report job: type={}, userId={}",
                request.getReportType(), userId, e);
            throw new ReportGenerationException(
                String.format("Failed to queue %s report: %s",
                    request.getReportType(), e.getMessage()),
                e
            );
        }
    }
}
