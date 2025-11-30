package com.evplus.report.service;

import com.evplus.report.exception.ReportGenerationException;
import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.dto.ReportResponse;
import com.evplus.report.model.entity.ReportJob;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ReportJobRepository;
import com.evplus.report.service.handler.HandlerRegistry;
import com.evplus.report.service.handler.ReportHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReportGeneratorService.
 */
@ExtendWith(MockitoExtension.class)
class ReportGeneratorServiceTest {

    @Mock
    private HandlerRegistry handlerRegistry;

    @Mock
    private ThresholdService thresholdService;

    @Mock
    private ReportJobRepository reportJobRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private ReportHandler mockHandler;

    @InjectMocks
    private ReportGeneratorService reportGeneratorService;

    private ReportRequest request;
    private MockReportCriteria criteria;
    private Integer userId = 123;
    private Integer districtId = 456;

    @BeforeEach
    void setUp() {
        // Set queue name via reflection
        ReflectionTestUtils.setField(reportGeneratorService, "queueName", "test-queue");

        criteria = new MockReportCriteria();
        request = new ReportRequest();
        request.setReportType(ReportType.USER_ACTIVITY);
        request.setCriteria(criteria);

        // Setup default handler behavior
        when(handlerRegistry.getHandler(any(ReportType.class))).thenReturn(mockHandler);
    }

    @Test
    void testGenerateReport_Sync_Success() throws Exception {
        // Setup
        when(mockHandler.exceedsAsyncThreshold(any())).thenReturn(false);

        MockReportData reportData = new MockReportData();
        reportData.setTotalRecords(100);
        when(mockHandler.generateReport(any())).thenReturn(reportData);

        // Execute
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        // Verify
        assertNotNull(response);
        assertEquals(ReportType.USER_ACTIVITY, response.getReportType());
        assertEquals(ReportStatus.COMPLETED, response.getStatus());
        assertEquals("Report generated successfully", response.getMessage());
        assertNotNull(response.getReportData());
        assertEquals(100, response.getTotalRecords());
        assertNotNull(response.getGeneratedAt());
        assertNull(response.getJobId());
        assertNull(response.getEstimatedCompletionTime());

        verify(mockHandler, times(1)).validateCriteria(criteria);
        verify(mockHandler, times(1)).exceedsAsyncThreshold(criteria);
        verify(mockHandler, times(1)).generateReport(criteria);
        verifyNoInteractions(reportJobRepository);
    }

    @Test
    void testGenerateReport_Async_Success() throws Exception {
        // Setup
        when(mockHandler.exceedsAsyncThreshold(any())).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mock\":\"json\"}");
        when(reportJobRepository.save(any(ReportJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        // Verify
        assertNotNull(response);
        assertEquals(ReportType.USER_ACTIVITY, response.getReportType());
        assertEquals(ReportStatus.QUEUED, response.getStatus());
        assertEquals("Report queued for processing", response.getMessage());
        assertNotNull(response.getJobId());
        assertNotNull(response.getEstimatedCompletionTime());
        assertNull(response.getReportData());

        verify(mockHandler, times(1)).validateCriteria(criteria);
        verify(mockHandler, times(1)).exceedsAsyncThreshold(criteria);
        verify(mockHandler, never()).generateReport(any());

        // Verify job was saved
        ArgumentCaptor<ReportJob> jobCaptor = ArgumentCaptor.forClass(ReportJob.class);
        verify(reportJobRepository, times(1)).save(jobCaptor.capture());

        ReportJob savedJob = jobCaptor.getValue();
        assertNotNull(savedJob.getReportId());
        assertEquals(districtId, savedJob.getDistrictId());
        assertEquals(userId, savedJob.getUserId());
        assertEquals(ReportType.USER_ACTIVITY, savedJob.getReportType());
        assertEquals("{\"mock\":\"json\"}", savedJob.getReportParams());
        assertEquals(ReportStatus.QUEUED, savedJob.getStatus());
        assertNotNull(savedJob.getRequestedDate());

        // Verify SQS message was sent
        verify(sqsTemplate, times(1)).send(eq("test-queue"), eq(savedJob.getReportId()));
    }

    @Test
    void testGenerateReport_ValidationFails() throws Exception {
        // Setup
        doThrow(new ValidationException("Invalid criteria"))
            .when(mockHandler).validateCriteria(any());

        // Execute & Verify
        assertThrows(ValidationException.class, () ->
            reportGeneratorService.generateReport(request, userId, districtId)
        );

        verify(mockHandler, times(1)).validateCriteria(criteria);
        verify(mockHandler, never()).exceedsAsyncThreshold(any());
        verify(mockHandler, never()).generateReport(any());
        verifyNoInteractions(reportJobRepository);
    }

    @Test
    void testGenerateReport_SyncGenerationFails() throws Exception {
        // Setup
        when(mockHandler.exceedsAsyncThreshold(any())).thenReturn(false);
        when(mockHandler.generateReport(any())).thenThrow(new RuntimeException("Generation failed"));

        // Execute & Verify
        ReportGenerationException exception = assertThrows(ReportGenerationException.class, () ->
            reportGeneratorService.generateReport(request, userId, districtId)
        );

        assertTrue(exception.getMessage().contains("Failed to generate USER_ACTIVITY report"));

        verify(mockHandler, times(1)).validateCriteria(criteria);
        verify(mockHandler, times(1)).exceedsAsyncThreshold(criteria);
        verify(mockHandler, times(1)).generateReport(criteria);
        verifyNoInteractions(reportJobRepository);
    }

    @Test
    void testGenerateReport_AsyncJobCreationFails() throws Exception {
        // Setup
        when(mockHandler.exceedsAsyncThreshold(any())).thenReturn(true);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"mock\":\"json\"}");
        when(reportJobRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        // Execute & Verify
        ReportGenerationException exception = assertThrows(ReportGenerationException.class, () ->
            reportGeneratorService.generateReport(request, userId, districtId)
        );

        assertTrue(exception.getMessage().contains("Failed to queue USER_ACTIVITY report"));

        verify(mockHandler, times(1)).validateCriteria(criteria);
        verify(mockHandler, times(1)).exceedsAsyncThreshold(criteria);
        verify(reportJobRepository, times(1)).save(any());
    }

    // Mock classes for testing
    static class MockReportCriteria extends ReportCriteria {
        @Override
        public ReportType getReportType() {
            return ReportType.USER_ACTIVITY;
        }
    }

    static class MockReportData extends ReportData {
        // Mock implementation
    }
}
