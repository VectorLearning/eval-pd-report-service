package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.exception.ReportJobNotFoundException;
import com.evplus.report.model.dto.DummyReportCriteria;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.repository.UserRepository;
import com.evplus.report.service.handler.HandlerRegistry;
import com.evplus.report.service.handler.ReportHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doReturn;

/**
 * Unit tests for AsyncReportProcessor.
 *
 * Tests cover:
 * - Happy path: successful job processing
 * - Idempotency: skipping already completed/processing jobs
 * - Error handling: job failure scenarios
 * - MDC correlation tracking
 */
@ExtendWith(MockitoExtension.class)
class AsyncReportProcessorTest {

    @Mock
    private ReportJobRepository reportJobRepository;

    @Mock
    private HandlerRegistry handlerRegistry;

    @Mock
    private ExcelReportGenerator excelReportGenerator;

    @Mock
    private S3Service s3Service;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private NotificationQueueService notificationQueueService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReportHandler reportHandler;

    @InjectMocks
    private AsyncReportProcessor asyncReportProcessor;

    private static final String TEST_JOB_ID = "test-job-123";
    private static final Integer TEST_DISTRICT_ID = 100;
    private static final Integer TEST_USER_ID = 200;
    private static final String TEST_CRITERIA_JSON = "{\"reportType\":\"DUMMY_TEST\",\"recordCount\":1000}";
    private static final String TEST_S3_KEY = "reports/100/test-job-123/DUMMY_TEST_test-job-123.xlsx";
    private static final String TEST_PRESIGNED_URL = "https://s3.amazonaws.com/bucket/key?signature=abc";

    private ReportJob testJob;
    private DummyReportCriteria testCriteria;
    private DummyReportData testReportData;
    private byte[] testExcelBytes;

    @BeforeEach
    void setUp() {
        // Set queue name via reflection
        ReflectionTestUtils.setField(asyncReportProcessor, "queueName", "test-queue");

        // Create test job
        testJob = new ReportJob();
        testJob.setReportId(TEST_JOB_ID);
        testJob.setDistrictId(TEST_DISTRICT_ID);
        testJob.setUserId(TEST_USER_ID);
        testJob.setReportType(ReportType.DUMMY_TEST);
        testJob.setReportParams(TEST_CRITERIA_JSON);
        testJob.setStatus(ReportStatus.QUEUED);
        testJob.setRequestedDate(LocalDateTime.now());

        // Create test criteria
        testCriteria = new DummyReportCriteria();
        testCriteria.setRecordCount(1000);

        // Create test report data
        testReportData = new DummyReportData();
        testReportData.setTotalRecords(1000);

        // Create test Excel bytes
        testExcelBytes = new byte[]{1, 2, 3, 4, 5};
    }

    @Test
    void processReportJob_Success() throws Exception {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);

        when(reportJobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(testJob));
        when(handlerRegistry.getHandler(ReportType.DUMMY_TEST)).thenReturn(reportHandler);
        doReturn(DummyReportCriteria.class).when(reportHandler).getCriteriaClass();
        when(objectMapper.readValue(TEST_CRITERIA_JSON, DummyReportCriteria.class))
            .thenReturn(testCriteria);
        when(reportHandler.generateReport(testCriteria)).thenReturn(testReportData);
        when(excelReportGenerator.generateExcel(testReportData)).thenReturn(testExcelBytes);
        when(s3Service.uploadReport(eq(TEST_DISTRICT_ID), eq(TEST_JOB_ID), eq(testExcelBytes), anyString()))
            .thenReturn(TEST_S3_KEY);
        when(s3Service.generatePresignedUrl(eq(TEST_S3_KEY), any())).thenReturn(TEST_PRESIGNED_URL);

        // Act
        asyncReportProcessor.processReportJob(message);

        // Assert
        // Verify save was called twice (once for PROCESSING, once for COMPLETED)
        verify(reportJobRepository, times(2)).save(any(ReportJob.class));

        // Verify final state of the job
        assertThat(testJob.getStatus()).isEqualTo(ReportStatus.COMPLETED);
        assertThat(testJob.getStartedDate()).isNotNull();
        assertThat(testJob.getCompletedDate()).isNotNull();
        assertThat(testJob.getS3Url()).isEqualTo(TEST_PRESIGNED_URL);
        assertThat(testJob.getFilename()).isEqualTo("DUMMY_TEST_test-job-123.xlsx");

        // Verify all services were called
        verify(reportHandler).generateReport(testCriteria);
        verify(excelReportGenerator).generateExcel(testReportData);
        verify(s3Service).uploadReport(eq(TEST_DISTRICT_ID), eq(TEST_JOB_ID), eq(testExcelBytes), anyString());
        verify(s3Service).generatePresignedUrl(eq(TEST_S3_KEY), any());
    }

    @Test
    void processReportJob_JobNotFound_ThrowsException() {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);
        when(reportJobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> asyncReportProcessor.processReportJob(message))
            .isInstanceOf(ReportGenerationException.class)
            .hasMessageContaining("Failed to process report job");

        verify(reportJobRepository, never()).save(any());
    }

    @Test
    void processReportJob_JobAlreadyCompleted_SkipsProcessing() throws Exception {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);
        testJob.setStatus(ReportStatus.COMPLETED);
        when(reportJobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(testJob));

        // Act
        asyncReportProcessor.processReportJob(message);

        // Assert - Should skip processing
        verify(reportJobRepository, never()).save(any());
        verify(reportHandler, never()).generateReport(any());
        verify(excelReportGenerator, never()).generateExcel(any());
        verify(s3Service, never()).uploadReport(any(), any(), any(), any());
    }

    @Test
    void processReportJob_JobAlreadyProcessing_SkipsProcessing() throws Exception {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);
        testJob.setStatus(ReportStatus.PROCESSING);
        when(reportJobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(testJob));

        // Act
        asyncReportProcessor.processReportJob(message);

        // Assert - Should skip processing
        verify(reportJobRepository, never()).save(any());
        verify(reportHandler, never()).generateReport(any());
        verify(excelReportGenerator, never()).generateExcel(any());
        verify(s3Service, never()).uploadReport(any(), any(), any(), any());
    }

    @Test
    void processReportJob_HandlerThrowsException_MarksJobAsFailed() throws Exception {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);

        when(reportJobRepository.findById(TEST_JOB_ID))
            .thenReturn(Optional.of(testJob))
            .thenReturn(Optional.of(testJob)); // Second call in handleJobFailure

        when(handlerRegistry.getHandler(ReportType.DUMMY_TEST)).thenReturn(reportHandler);
        doReturn(DummyReportCriteria.class).when(reportHandler).getCriteriaClass();
        when(objectMapper.readValue(TEST_CRITERIA_JSON, DummyReportCriteria.class))
            .thenReturn(testCriteria);
        when(reportHandler.generateReport(testCriteria))
            .thenThrow(new RuntimeException("Test error"));

        // Act & Assert
        assertThatThrownBy(() -> asyncReportProcessor.processReportJob(message))
            .isInstanceOf(ReportGenerationException.class);

        // Verify job was marked as FAILED
        ArgumentCaptor<ReportJob> jobCaptor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobRepository, atLeast(2)).save(jobCaptor.capture());

        ReportJob failedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertThat(failedJob.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).contains("Test error");
        assertThat(failedJob.getCompletedDate()).isNotNull();
    }

    @Test
    void processReportJob_S3UploadFails_MarksJobAsFailed() throws Exception {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);

        when(reportJobRepository.findById(TEST_JOB_ID))
            .thenReturn(Optional.of(testJob))
            .thenReturn(Optional.of(testJob)); // Second call in handleJobFailure

        when(handlerRegistry.getHandler(ReportType.DUMMY_TEST)).thenReturn(reportHandler);
        doReturn(DummyReportCriteria.class).when(reportHandler).getCriteriaClass();
        when(objectMapper.readValue(TEST_CRITERIA_JSON, DummyReportCriteria.class))
            .thenReturn(testCriteria);
        when(reportHandler.generateReport(testCriteria)).thenReturn(testReportData);
        when(excelReportGenerator.generateExcel(testReportData)).thenReturn(testExcelBytes);
        when(s3Service.uploadReport(any(), any(), any(), any()))
            .thenThrow(new ReportGenerationException("S3 upload failed"));

        // Act & Assert
        assertThatThrownBy(() -> asyncReportProcessor.processReportJob(message))
            .isInstanceOf(ReportGenerationException.class);

        // Verify job was marked as FAILED
        ArgumentCaptor<ReportJob> jobCaptor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobRepository, atLeast(2)).save(jobCaptor.capture());

        ReportJob failedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertThat(failedJob.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).contains("S3 upload failed");
    }

    @Test
    void processReportJob_ExcelGenerationFails_MarksJobAsFailed() throws Exception {
        // Arrange
        Message<String> message = new GenericMessage<>(TEST_JOB_ID);

        when(reportJobRepository.findById(TEST_JOB_ID))
            .thenReturn(Optional.of(testJob))
            .thenReturn(Optional.of(testJob)); // Second call in handleJobFailure

        when(handlerRegistry.getHandler(ReportType.DUMMY_TEST)).thenReturn(reportHandler);
        doReturn(DummyReportCriteria.class).when(reportHandler).getCriteriaClass();
        when(objectMapper.readValue(TEST_CRITERIA_JSON, DummyReportCriteria.class))
            .thenReturn(testCriteria);
        when(reportHandler.generateReport(testCriteria)).thenReturn(testReportData);
        when(excelReportGenerator.generateExcel(testReportData))
            .thenThrow(new ReportGenerationException("Excel generation failed"));

        // Act & Assert
        assertThatThrownBy(() -> asyncReportProcessor.processReportJob(message))
            .isInstanceOf(ReportGenerationException.class);

        // Verify job was marked as FAILED
        ArgumentCaptor<ReportJob> jobCaptor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobRepository, atLeast(2)).save(jobCaptor.capture());

        ReportJob failedJob = jobCaptor.getAllValues().get(jobCaptor.getAllValues().size() - 1);
        assertThat(failedJob.getStatus()).isEqualTo(ReportStatus.FAILED);
        assertThat(failedJob.getErrorMessage()).contains("Excel generation failed");
    }

    @Test
    void processJob_DeserializationFails_ThrowsException() throws Exception {
        // Arrange
        when(reportJobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.of(testJob));
        when(handlerRegistry.getHandler(ReportType.DUMMY_TEST)).thenReturn(reportHandler);
        doReturn(DummyReportCriteria.class).when(reportHandler).getCriteriaClass();
        when(objectMapper.readValue(TEST_CRITERIA_JSON, DummyReportCriteria.class))
            .thenThrow(new RuntimeException("JSON parse error"));

        // Act & Assert
        assertThatThrownBy(() -> asyncReportProcessor.processJob(TEST_JOB_ID))
            .isInstanceOf(ReportGenerationException.class)
            .hasMessageContaining("Failed to process job");
    }

    @Test
    void handleJobFailure_JobNotFound_LogsError() {
        // Arrange
        when(reportJobRepository.findById(TEST_JOB_ID)).thenReturn(Optional.empty());

        // Act - use reflection to call private method
        ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "handleJobFailure",
            TEST_JOB_ID,
            new RuntimeException("Test error")
        );

        // Assert - no exception should be thrown
        verify(reportJobRepository).findById(TEST_JOB_ID);
        verify(reportJobRepository, never()).save(any());
    }

    @Test
    void truncateErrorMessage_LongMessage_Truncates() {
        // Arrange
        String longMessage = "X".repeat(1500);

        // Act
        String truncated = ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "truncateErrorMessage",
            longMessage
        );

        // Assert
        assertThat(truncated).hasSize(1015); // 1000 + "... (truncated)" = 1015
        assertThat(truncated).endsWith("... (truncated)");
    }

    @Test
    void truncateErrorMessage_ShortMessage_NoTruncation() {
        // Arrange
        String shortMessage = "Short error message";

        // Act
        String result = ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "truncateErrorMessage",
            shortMessage
        );

        // Assert
        assertThat(result).isEqualTo(shortMessage);
    }

    @Test
    void truncateErrorMessage_NullMessage_ReturnsDefault() {
        // Act
        String result = ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "truncateErrorMessage",
            new Object[]{null}
        );

        // Assert
        assertThat(result).isEqualTo("Unknown error");
    }

    @Test
    void maskUrl_WithQueryParams_MasksParams() {
        // Arrange
        String url = "https://s3.amazonaws.com/bucket/key?signature=secret&token=abc";

        // Act
        String masked = ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "maskUrl",
            url
        );

        // Assert
        assertThat(masked).isEqualTo("https://s3.amazonaws.com/bucket/key?[MASKED]");
    }

    @Test
    void maskUrl_WithoutQueryParams_NoMasking() {
        // Arrange
        String url = "https://s3.amazonaws.com/bucket/key";

        // Act
        String masked = ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "maskUrl",
            url
        );

        // Assert
        assertThat(masked).isEqualTo(url);
    }

    @Test
    void maskUrl_NullUrl_ReturnsNull() {
        // Act
        String masked = ReflectionTestUtils.invokeMethod(
            asyncReportProcessor,
            "maskUrl",
            new Object[]{null}
        );

        // Assert
        assertThat(masked).isNull();
    }
}
