package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;

/**
 * S3 Service for report file management.
 *
 * Handles:
 * - Uploading generated reports to S3
 * - Generating presigned URLs for secure temporary access
 * - Downloading reports for direct streaming
 */
@Service
public class S3Service {

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);
    private static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    public S3Service(
        S3Client s3Client,
        S3Presigner s3Presigner,
        @Value("${aws.s3.bucket-name}") String bucketName
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    /**
     * Upload report to S3.
     *
     * S3 key pattern: reports/{districtId}/{jobId}/{filename}
     * This pattern enables:
     * - Easy filtering by district
     * - Unique job identification
     * - Organized file structure
     *
     * @param districtId District ID for folder organization
     * @param jobId Job ID for unique identification
     * @param reportData Excel file as byte array
     * @param filename Original filename (e.g., "USER_ACTIVITY_123.xlsx")
     * @return S3 key for later retrieval
     * @throws ReportGenerationException if upload fails
     */
    public String uploadReport(Integer districtId, String jobId, byte[] reportData, String filename) {
        String s3Key = String.format("reports/%d/%s/%s", districtId, jobId, filename);

        logger.info("Uploading report to S3: bucket={}, key={}, size={} bytes",
            bucketName, s3Key, reportData.length);

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(CONTENT_TYPE)
                .contentLength((long) reportData.length)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();

            s3Client.putObject(putRequest, RequestBody.fromBytes(reportData));

            logger.info("Successfully uploaded report to S3: key={}", s3Key);
            return s3Key;

        } catch (S3Exception e) {
            logger.error("Failed to upload report to S3: bucket={}, key={}", bucketName, s3Key, e);
            throw new ReportGenerationException("Failed to upload report to S3: " + e.getMessage(), e);
        }
    }

    /**
     * Generate presigned URL for secure temporary download access.
     *
     * Presigned URLs provide time-limited access to S3 objects without
     * requiring AWS credentials. Perfect for email links.
     *
     * @param s3Key S3 object key
     * @param expiration Expiration duration (e.g., Duration.ofDays(7))
     * @return Presigned URL string
     * @throws ReportGenerationException if URL generation fails
     */
    public String generatePresignedUrl(String s3Key, Duration expiration) {
        logger.info("Generating presigned URL: key={}, expiration={}", s3Key, expiration);

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(getRequest)
                .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            String url = presignedRequest.url().toString();

            logger.info("Successfully generated presigned URL: key={}, url={}", s3Key, maskUrl(url));
            return url;

        } catch (S3Exception e) {
            logger.error("Failed to generate presigned URL: key={}", s3Key, e);
            throw new ReportGenerationException("Failed to generate download URL: " + e.getMessage(), e);
        }
    }

    /**
     * Download report file from S3.
     *
     * Used for direct download endpoints where the service streams
     * the file to the client rather than redirecting to S3.
     *
     * @param s3Key S3 object key
     * @return Report file as byte array
     * @throws ReportGenerationException if download fails
     */
    public byte[] downloadReport(String s3Key) {
        logger.info("Downloading report from S3: key={}", s3Key);

        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            byte[] reportData = s3Client.getObject(getRequest, ResponseTransformer.toBytes()).asByteArray();

            logger.info("Successfully downloaded report from S3: key={}, size={} bytes", s3Key, reportData.length);
            return reportData;

        } catch (NoSuchKeyException e) {
            logger.error("Report not found in S3: key={}", s3Key);
            throw new ReportGenerationException("Report file not found in S3", e);
        } catch (S3Exception e) {
            logger.error("Failed to download report from S3: key={}", s3Key, e);
            throw new ReportGenerationException("Failed to download report from S3: " + e.getMessage(), e);
        }
    }

    /**
     * Mask presigned URL for logging (hide sensitive query parameters).
     */
    private String maskUrl(String url) {
        int queryIndex = url.indexOf('?');
        return queryIndex > 0 ? url.substring(0, queryIndex) + "?[MASKED]" : url;
    }
}
