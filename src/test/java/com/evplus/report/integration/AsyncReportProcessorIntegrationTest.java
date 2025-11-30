package com.evplus.report.integration;

import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ReportJobRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Integration test for AsyncReportProcessor using LocalStack SQS and S3.
 *
 * Prerequisites:
 * - LocalStack must be running via docker-compose: docker-compose -f docker-compose-localstack.yml up -d
 * - SQS queue 'ev-plus-reporting-local-queue' is created automatically by localstack-init.sh
 * - S3 bucket 'ev-plus-reports-local' is created automatically by localstack-init.sh
 *
 * Tests:
 * - End-to-end async report processing flow
 * - SQS message consumption and job status updates
 * - Excel generation and S3 upload
 * - Idempotency handling for duplicate messages
 * - Error handling and job failure scenarios
 */
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AsyncReportProcessorIntegrationTest {

    @Autowired
    private ReportJobRepository reportJobRepository;

    @Autowired
    private SqsTemplate sqsTemplate;

    @Autowired
    private S3Client s3Client;

    @Value("${aws.sqs.queue-name}")
    private String queueName;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String TEST_CRITERIA_JSON = "{\"reportType\":\"DUMMY_TEST\",\"recordCount\":100}";

    @Test
    @Order(1)
    @DisplayName("Should process async report job end-to-end successfully")
    void testAsyncReportProcessing_Success() {
        // Given: Create a test report job
        String jobId = UUID.randomUUID().toString();
        ReportJob job = createTestJob(jobId, TEST_CRITERIA_JSON);
        reportJobRepository.save(job);

        // When: Send SQS message to trigger processing
        sqsTemplate.send(queueName, jobId);

        // Then: Wait for job to be processed (timeout after 30 seconds)
        await()
            .atMost(30, SECONDS)
            .pollInterval(1, SECONDS)
            .untilAsserted(() -> {
                ReportJob updatedJob = reportJobRepository.findById(jobId).orElseThrow();
                assertThat(updatedJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
            });

        // Verify job details
        ReportJob completedJob = reportJobRepository.findById(jobId).orElseThrow();
        assertThat(completedJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(completedJob.getStartedDate()).isNotNull();
        assertThat(completedJob.getCompletedDate()).isNotNull();
        assertThat(completedJob.getS3Url()).isNotNull();
        assertThat(completedJob.getFilename()).isEqualTo("DUMMY_TEST_" + jobId + ".xlsx");
        assertThat(completedJob.getErrorMessage()).isNull();

        // Verify Excel file was uploaded to S3
        String expectedS3Key = "reports/" + job.getDistrictId() + "/" + jobId + "/DUMMY_TEST_" + jobId + ".xlsx";
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
            .bucket(bucketName)
            .key(expectedS3Key)
            .build();

        HeadObjectResponse headResponse = s3Client.headObject(headRequest);
        assertThat(headResponse).isNotNull();
        assertThat(headResponse.contentLength()).isGreaterThan(0);
        assertThat(headResponse.contentType())
            .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        // Cleanup
        reportJobRepository.deleteById(jobId);
    }

    @Test
    @Order(2)
    @DisplayName("Should handle idempotency for duplicate SQS messages")
    void testAsyncReportProcessing_Idempotency() {
        // Given: Create and save a test job
        String jobId = UUID.randomUUID().toString();
        ReportJob job = createTestJob(jobId, TEST_CRITERIA_JSON);
        reportJobRepository.save(job);

        // When: Send the same message twice
        sqsTemplate.send(queueName, jobId);
        sqsTemplate.send(queueName, jobId); // Duplicate message

        // Then: Wait for processing to complete
        await()
            .atMost(30, SECONDS)
            .pollInterval(1, SECONDS)
            .untilAsserted(() -> {
                ReportJob updatedJob = reportJobRepository.findById(jobId).orElseThrow();
                assertThat(updatedJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
            });

        // Verify job was processed only once (no errors from duplicate processing)
        ReportJob completedJob = reportJobRepository.findById(jobId).orElseThrow();
        assertThat(completedJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(completedJob.getErrorMessage()).isNull();

        // Cleanup
        reportJobRepository.deleteById(jobId);
    }

    @Test
    @Order(3)
    @DisplayName("Should handle job not found gracefully")
    void testAsyncReportProcessing_JobNotFound() {
        // Given: Non-existent job ID
        String nonExistentJobId = UUID.randomUUID().toString();

        // When: Send SQS message for non-existent job
        sqsTemplate.send(queueName, nonExistentJobId);

        // Then: Wait a few seconds to ensure message is processed
        await()
            .during(5, SECONDS)
            .atMost(10, SECONDS)
            .untilAsserted(() -> {
                // Job should not exist in database
                assertThat(reportJobRepository.findById(nonExistentJobId)).isEmpty();
            });

        // Verify no S3 file was created
        String s3Key = "reports/999/" + nonExistentJobId + "/DUMMY_TEST_" + nonExistentJobId + ".xlsx";
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();
            s3Client.headObject(headRequest);
            Assertions.fail("Expected NoSuchKeyException but file exists");
        } catch (NoSuchKeyException e) {
            // Expected - file should not exist
            assertThat(e).isInstanceOf(NoSuchKeyException.class);
        }
    }

    @Test
    @Order(4)
    @DisplayName("Should skip processing when job is already completed")
    void testAsyncReportProcessing_AlreadyCompleted() {
        // Given: Create a job that's already marked as COMPLETED
        String jobId = UUID.randomUUID().toString();
        ReportJob job = createTestJob(jobId, TEST_CRITERIA_JSON);
        job.setStatus(ReportStatus.COMPLETED);
        job.setStartedDate(LocalDateTime.now().minusMinutes(5));
        job.setCompletedDate(LocalDateTime.now().minusMinutes(1));
        job.setS3Url("https://s3.example.com/existing-file.xlsx");
        job.setFilename("DUMMY_TEST_" + jobId + ".xlsx");
        reportJobRepository.save(job);

        // When: Send SQS message for already-completed job
        sqsTemplate.send(queueName, jobId);

        // Then: Wait a few seconds to ensure message is processed
        await()
            .during(5, SECONDS)
            .atMost(10, SECONDS)
            .untilAsserted(() -> {
                ReportJob unchangedJob = reportJobRepository.findById(jobId).orElseThrow();
                // Job should remain COMPLETED with original data
                assertThat(unchangedJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
                assertThat(unchangedJob.getS3Url()).isEqualTo("https://s3.example.com/existing-file.xlsx");
            });

        // Cleanup
        reportJobRepository.deleteById(jobId);
    }

    @Test
    @Order(5)
    @DisplayName("Should handle invalid JSON criteria gracefully")
    void testAsyncReportProcessing_InvalidCriteria() {
        // Given: Create a job with invalid JSON criteria
        String jobId = UUID.randomUUID().toString();
        ReportJob job = createTestJob(jobId, "{invalid-json}");
        reportJobRepository.save(job);

        // When: Send SQS message
        sqsTemplate.send(queueName, jobId);

        // Then: Wait for processing to complete with failure
        await()
            .atMost(30, SECONDS)
            .pollInterval(1, SECONDS)
            .untilAsserted(() -> {
                ReportJob updatedJob = reportJobRepository.findById(jobId).orElseThrow();
                assertThat(updatedJob.getStatus()).isEqualTo(ReportStatus.FAILED);
            });

        // Verify job failed with error message
        ReportJob failedJob = reportJobRepository.findById(jobId).orElseThrow();
        assertThat(failedJob.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).isNotNull();
        assertThat(failedJob.getCompletedDate()).isNotNull();

        // Cleanup
        reportJobRepository.deleteById(jobId);
    }

    /**
     * Helper method to create a test ReportJob.
     */
    private ReportJob createTestJob(String jobId, String criteriaJson) {
        ReportJob job = new ReportJob();
        job.setReportId(jobId);
        job.setDistrictId(100);
        job.setUserId(200);
        job.setReportType(ReportType.DUMMY_TEST);
        job.setReportParams(criteriaJson);
        job.setStatus(ReportStatus.QUEUED);
        job.setRequestedDate(LocalDateTime.now());
        return job;
    }
}
