package com.evplus.report.integration;

import com.evplus.report.model.dto.DummyReportCriteria;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.dto.ReportResponse;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.service.ReportGeneratorService;
import com.evplus.report.service.handler.DummyReportHandler;
import com.evplus.report.service.handler.HandlerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for DummyReportHandler using Spring Boot context.
 * Validates:
 * - Handler auto-registration in HandlerRegistry
 * - End-to-end report generation flow
 * - Integration with ReportGeneratorService
 *
 * Uses "local" profile to avoid database routing configuration complexity.
 */
@SpringBootTest
@ActiveProfiles("local")
class DummyReportIntegrationTest {

    @Autowired
    private HandlerRegistry handlerRegistry;

    @Autowired
    private DummyReportHandler dummyReportHandler;

    @Autowired
    private ReportGeneratorService reportGeneratorService;

    @Test
    void testHandlerAutoRegistration() {
        // Verify DummyReportHandler is auto-registered in HandlerRegistry
        assertTrue(handlerRegistry.isSupported(ReportType.DUMMY_TEST),
                "DummyReportHandler should be auto-registered");

        var handler = handlerRegistry.getHandler(ReportType.DUMMY_TEST);
        assertNotNull(handler);
        assertInstanceOf(DummyReportHandler.class, handler);
    }

    @Test
    void testDummyReportHandlerBean() {
        // Verify DummyReportHandler is available as a Spring bean
        assertNotNull(dummyReportHandler);
        assertEquals(ReportType.DUMMY_TEST, dummyReportHandler.getReportType());
    }

    @Test
    void testAsyncReportGeneration_SmallDataset() {
        // Create request with small dataset
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(100);
        criteria.setTestParameter("integration-test-small");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 100;

        // Generate report (should go async because handler always returns true for exceedsAsyncThreshold)
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        // Verify async response
        assertNotNull(response);
        assertEquals(com.evplus.report.model.enums.ReportStatus.QUEUED, response.getStatus());
        assertNotNull(response.getJobId());
        assertNotNull(response.getEstimatedCompletionTime());
        assertNull(response.getReportData()); // Data should be null for async response
    }

    @Test
    void testAsyncReportGeneration_LargeDataset() {
        // Create request with large dataset
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(50000);
        criteria.setTestParameter("integration-test-large");

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 100;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        // Verify async response
        assertNotNull(response);
        assertEquals(com.evplus.report.model.enums.ReportStatus.QUEUED, response.getStatus());
        assertNotNull(response.getJobId());
    }

    @Test
    void testDirectHandlerInvocation() throws Exception {
        // Test direct handler invocation (what happens when SQS message is processed)
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(1000);
        criteria.setTestParameter("direct-test");

        // Validate criteria
        assertDoesNotThrow(() -> dummyReportHandler.validateCriteria(criteria));

        // Generate report
        var reportData = dummyReportHandler.generateReport(criteria);

        assertNotNull(reportData);
        assertInstanceOf(DummyReportData.class, reportData);

        DummyReportData dummyData = (DummyReportData) reportData;
        assertEquals(1000, dummyData.getRecords().size());
        assertEquals(1000, dummyData.getTotalRecords());
        assertNotNull(dummyData.getGeneratedAt());
    }

    @Test
    void testValidationIntegration() {
        // Test validation through the full stack
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(-100); // Invalid

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.DUMMY_TEST);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 100;

        // Should throw validation exception
        assertThrows(Exception.class,
                () -> reportGeneratorService.generateReport(request, userId, districtId));
    }

    @Test
    void testThresholdCheck() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(1000);

        // DummyReportHandler should always exceed threshold to test async flow
        assertTrue(dummyReportHandler.exceedsAsyncThreshold(criteria));
    }
}
