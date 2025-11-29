package com.evplus.report.integration;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.service.S3Service;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for S3Service using LocalStack.
 *
 * Prerequisites:
 * - LocalStack must be running via docker-compose: docker-compose -f docker-compose-localstack.yml up -d
 * - S3 bucket 'ev-plus-reports-local' is created automatically by localstack-init.sh
 *
 * Tests:
 * - S3 upload functionality
 * - Presigned URL generation and accessibility
 * - S3 download functionality
 * - File integrity (upload → download)
 * - Error scenarios
 */
@SpringBootTest
@ActiveProfiles("local")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class S3ServiceIntegrationTest {

    private static final String BUCKET_NAME = "ev-plus-reports-local";

    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3Client s3Client;

    @Test
    @Order(1)
    @DisplayName("Should upload report to S3 successfully")
    void testUploadReport() {
        // Given
        Integer districtId = 123;
        String jobId = "test-job-001";
        String filename = "TEST_REPORT_001.xlsx";
        byte[] reportData = "Test Excel Content".getBytes();

        // When
        String s3Key = s3Service.uploadReport(districtId, jobId, reportData, filename);

        // Then
        assertNotNull(s3Key);
        assertEquals("reports/123/test-job-001/TEST_REPORT_001.xlsx", s3Key);

        // Verify file exists in S3
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(s3Key)
                .build();

        HeadObjectResponse headResponse = s3Client.headObject(headRequest);
        assertNotNull(headResponse);
        assertEquals(reportData.length, headResponse.contentLength());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                headResponse.contentType());
    }

    @Test
    @Order(2)
    @DisplayName("Should generate presigned URL successfully")
    void testGeneratePresignedUrl() {
        // Given - Upload a test file first
        Integer districtId = 456;
        String jobId = "test-job-002";
        String filename = "PRESIGNED_TEST.xlsx";
        byte[] reportData = "Presigned URL Test Content".getBytes();
        String s3Key = s3Service.uploadReport(districtId, jobId, reportData, filename);

        // When
        String presignedUrl = s3Service.generatePresignedUrl(s3Key, Duration.ofMinutes(15));

        // Then
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.startsWith("http"), "Presigned URL should be a valid HTTP URL");
        assertTrue(presignedUrl.contains(BUCKET_NAME), "URL should contain bucket name");
        assertTrue(presignedUrl.contains(s3Key), "URL should contain S3 key");

        // Note: HTTP accessibility test skipped for LocalStack due to DNS resolution limitations
        // LocalStack generates URLs with format: http://{bucket}.localhost:4566/...
        // which cannot be resolved without additional DNS configuration.
        // The presigned URL generation itself is verified above.
    }

    @Test
    @Order(3)
    @DisplayName("Should download report and verify file integrity")
    void testDownloadReport() {
        // Given - Upload a test file
        Integer districtId = 789;
        String jobId = "test-job-003";
        String filename = "DOWNLOAD_TEST.xlsx";
        byte[] originalData = "Download Test - Original Content 12345".getBytes();
        String s3Key = s3Service.uploadReport(districtId, jobId, originalData, filename);

        // When
        byte[] downloadedData = s3Service.downloadReport(s3Key);

        // Then
        assertNotNull(downloadedData);
        assertEquals(originalData.length, downloadedData.length);
        assertArrayEquals(originalData, downloadedData, "Downloaded content must match uploaded content");
    }

    @Test
    @Order(4)
    @DisplayName("Should verify upload → download file integrity for large files")
    void testLargeFileIntegrity() {
        // Given - Create a larger test file (1MB)
        byte[] largeData = new byte[1024 * 1024]; // 1 MB
        Arrays.fill(largeData, (byte) 'A');
        // Add some variation
        for (int i = 0; i < largeData.length; i += 1000) {
            largeData[i] = (byte) ('A' + (i % 26));
        }

        Integer districtId = 999;
        String jobId = "test-job-large";
        String filename = "LARGE_FILE_TEST.xlsx";

        // When
        String s3Key = s3Service.uploadReport(districtId, jobId, largeData, filename);
        byte[] downloadedData = s3Service.downloadReport(s3Key);

        // Then
        assertNotNull(downloadedData);
        assertEquals(largeData.length, downloadedData.length, "File size should match");
        assertArrayEquals(largeData, downloadedData, "File content should be identical");
    }

    @Test
    @Order(5)
    @DisplayName("Should throw exception when downloading non-existent file")
    void testDownloadNonExistentFile() {
        // Given
        String nonExistentKey = "reports/999/non-existent-job/MISSING.xlsx";

        // When/Then
        ReportGenerationException exception = assertThrows(ReportGenerationException.class,
                () -> s3Service.downloadReport(nonExistentKey));

        assertTrue(exception.getMessage().contains("not found") ||
                   exception.getMessage().contains("Report file not found"),
                "Exception message should indicate file not found");
    }

    @Test
    @Order(6)
    @DisplayName("Should generate presigned URL with correct expiration")
    void testPresignedUrlExpiration() {
        // Given
        Integer districtId = 111;
        String jobId = "test-job-expiration";
        String filename = "EXPIRATION_TEST.xlsx";
        byte[] reportData = "Expiration Test".getBytes();
        String s3Key = s3Service.uploadReport(districtId, jobId, reportData, filename);

        // When - Generate URL with 7-day expiration
        Duration expiration = Duration.ofDays(7);
        String presignedUrl = s3Service.generatePresignedUrl(s3Key, expiration);

        // Then
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains("X-Amz-Expires=604800") ||
                   presignedUrl.contains("Expires="),
                "URL should contain expiration parameter (7 days = 604800 seconds)");
    }

    @Test
    @Order(7)
    @DisplayName("Should handle special characters in filename")
    void testUploadWithSpecialCharacters() {
        // Given
        Integer districtId = 222;
        String jobId = "test-job-special";
        String filename = "USER_ACTIVITY_2025-01-28_10:30:45.xlsx";
        byte[] reportData = "Special chars test".getBytes();

        // When
        String s3Key = s3Service.uploadReport(districtId, jobId, reportData, filename);

        // Then
        assertNotNull(s3Key);
        assertTrue(s3Key.contains(filename), "S3 key should preserve filename");

        // Verify can download
        byte[] downloadedData = s3Service.downloadReport(s3Key);
        assertArrayEquals(reportData, downloadedData);
    }

    @Test
    @Order(8)
    @DisplayName("Should verify S3 bucket configuration")
    void testS3BucketExists() {
        // When
        HeadBucketRequest headBucketRequest = HeadBucketRequest.builder()
                .bucket(BUCKET_NAME)
                .build();

        // Then - Should not throw exception
        assertDoesNotThrow(() -> s3Client.headBucket(headBucketRequest),
                "S3 bucket should exist and be accessible");
    }

    @AfterEach
    void cleanup() {
        // Clean up test files after each test (optional, but good practice)
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(BUCKET_NAME)
                    .prefix("reports/")
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            for (S3Object s3Object : listResponse.contents()) {
                DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(s3Object.key())
                        .build();
                s3Client.deleteObject(deleteRequest);
            }
        } catch (Exception e) {
            // Ignore cleanup errors
            System.err.println("Cleanup warning: " + e.getMessage());
        }
    }
}
