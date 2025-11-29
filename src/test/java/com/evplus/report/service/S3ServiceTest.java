package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for S3Service.
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3Service s3Service;
    private static final String BUCKET_NAME = "test-bucket";

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client, s3Presigner, BUCKET_NAME);
    }

    @Test
    void uploadReport_shouldUploadToS3WithCorrectKey() {
        // Given
        Integer districtId = 123;
        String jobId = "job-456";
        String filename = "USER_ACTIVITY_job-456.xlsx";
        byte[] reportData = "test data".getBytes();

        PutObjectResponse putResponse = PutObjectResponse.builder().build();
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenReturn(putResponse);

        // When
        String s3Key = s3Service.uploadReport(districtId, jobId, reportData, filename);

        // Then
        assertEquals("reports/123/job-456/USER_ACTIVITY_job-456.xlsx", s3Key);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals("reports/123/job-456/USER_ACTIVITY_job-456.xlsx", capturedRequest.key());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            capturedRequest.contentType());
        assertEquals((long) reportData.length, capturedRequest.contentLength());
    }

    @Test
    void uploadReport_whenS3ThrowsException_shouldThrowReportGenerationException() {
        // Given
        Integer districtId = 123;
        String jobId = "job-456";
        String filename = "report.xlsx";
        byte[] reportData = "test".getBytes();

        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
            .thenThrow(S3Exception.builder().message("S3 error").build());

        // When/Then
        assertThrows(ReportGenerationException.class, () -> {
            s3Service.uploadReport(districtId, jobId, reportData, filename);
        });
    }

    @Test
    void generatePresignedUrl_shouldReturnValidUrl() throws MalformedURLException {
        // Given
        String s3Key = "reports/123/job-456/report.xlsx";
        Duration expiration = Duration.ofDays(7);
        String expectedUrl = "https://bucket.s3.amazonaws.com/reports/123/job-456/report.xlsx?signature=xyz";

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenReturn(presignedRequest);

        // When
        String url = s3Service.generatePresignedUrl(s3Key, expiration);

        // Then
        assertEquals(expectedUrl, url);

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());

        GetObjectPresignRequest capturedRequest = captor.getValue();
        assertEquals(expiration, capturedRequest.signatureDuration());
        assertEquals(BUCKET_NAME, capturedRequest.getObjectRequest().bucket());
        assertEquals(s3Key, capturedRequest.getObjectRequest().key());
    }

    @Test
    void generatePresignedUrl_whenS3ThrowsException_shouldThrowReportGenerationException() {
        // Given
        String s3Key = "reports/123/job-456/report.xlsx";
        Duration expiration = Duration.ofHours(1);

        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
            .thenThrow(S3Exception.builder().message("S3 presigner error").build());

        // When/Then
        assertThrows(ReportGenerationException.class, () -> {
            s3Service.generatePresignedUrl(s3Key, expiration);
        });
    }

    @Test
    void downloadReport_shouldReturnFileBytes() {
        // Given
        String s3Key = "reports/123/job-456/report.xlsx";
        byte[] expectedData = "Excel file content".getBytes();

        @SuppressWarnings("unchecked")
        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(expectedData);

        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenReturn(responseBytes);

        // When
        byte[] actualData = s3Service.downloadReport(s3Key);

        // Then
        assertArrayEquals(expectedData, actualData);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(captor.capture(), any(ResponseTransformer.class));

        GetObjectRequest capturedRequest = captor.getValue();
        assertEquals(BUCKET_NAME, capturedRequest.bucket());
        assertEquals(s3Key, capturedRequest.key());
    }

    @Test
    void downloadReport_whenFileNotFound_shouldThrowReportGenerationException() {
        // Given
        String s3Key = "reports/123/job-456/nonexistent.xlsx";

        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenThrow(NoSuchKeyException.builder().message("Key not found").build());

        // When/Then
        ReportGenerationException exception = assertThrows(ReportGenerationException.class, () -> {
            s3Service.downloadReport(s3Key);
        });

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    void downloadReport_whenS3ThrowsException_shouldThrowReportGenerationException() {
        // Given
        String s3Key = "reports/123/job-456/report.xlsx";

        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class)))
            .thenThrow(S3Exception.builder().message("S3 download error").build());

        // When/Then
        assertThrows(ReportGenerationException.class, () -> {
            s3Service.downloadReport(s3Key);
        });
    }
}
