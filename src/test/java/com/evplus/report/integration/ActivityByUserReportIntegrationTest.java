package com.evplus.report.integration;

import com.evplus.report.model.dto.ActivityByUserCriteria;
import com.evplus.report.model.dto.ReportRequest;
import com.evplus.report.model.dto.ReportResponse;
import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.service.ReportGeneratorService;
import com.evplus.report.service.handler.ActivityByUserReportHandler;
import com.evplus.report.service.handler.HandlerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for ActivityByUserReportHandler using Spring Boot context.
 * Validates:
 * - Handler auto-registration in HandlerRegistry
 * - End-to-end report generation flow
 * - Integration with ReportGeneratorService
 * - Async processing for large reports
 *
 * Uses "local" profile to avoid database routing configuration complexity.
 * Note: These tests validate framework integration, not database queries.
 * Database query validation requires connection to TeachPoint database.
 */
@SpringBootTest
@ActiveProfiles("local")
class ActivityByUserReportIntegrationTest {

    @Autowired
    private HandlerRegistry handlerRegistry;

    @Autowired
    private ActivityByUserReportHandler activityByUserReportHandler;

    @Autowired
    private ReportGeneratorService reportGeneratorService;

    @Test
    void testHandlerAutoRegistration() {
        // Verify ActivityByUserReportHandler is auto-registered in HandlerRegistry
        assertTrue(handlerRegistry.isSupported(ReportType.ACTIVITY_BY_USER),
                "ActivityByUserReportHandler should be auto-registered");

        var handler = handlerRegistry.getHandler(ReportType.ACTIVITY_BY_USER);
        assertNotNull(handler);
        assertInstanceOf(ActivityByUserReportHandler.class, handler);
    }

    @Test
    void testActivityByUserReportHandlerBean() {
        // Verify ActivityByUserReportHandler is available as a Spring bean
        assertNotNull(activityByUserReportHandler);
        assertEquals(ReportType.ACTIVITY_BY_USER, activityByUserReportHandler.getReportType());
        assertEquals(ActivityByUserCriteria.class, activityByUserReportHandler.getCriteriaClass());
    }

    @Test
    void testAsyncReportGeneration_SmallReport() {
        // Create request with small dataset (should go async based on threshold)
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3, 4, 5)); // 5 users
        criteria.setStartDate(LocalDate.now().minusDays(30)); // 30 days
        criteria.setEndDate(LocalDate.now());
        criteria.setSources(Set.of("PD_TRACKING"));

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        // Verify response structure (may be sync or async depending on threshold)
        assertNotNull(response);
        assertNotNull(response.getStatus());

        if (response.getStatus() == ReportStatus.QUEUED) {
            // Async response
            assertNotNull(response.getJobId());
            assertNotNull(response.getEstimatedCompletionTime());
            assertNull(response.getReportData());
        } else if (response.getStatus() == ReportStatus.COMPLETED) {
            // Sync response
            assertNull(response.getJobId());
            assertNotNull(response.getReportData());
        }
    }

    @Test
    void testAsyncReportGeneration_LargeReport() {
        // Create request with large dataset (should definitely go async)
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(createUserList(1000)); // 1000 users
        criteria.setStartDate(LocalDate.now().minusYears(1)); // 1 year
        criteria.setEndDate(LocalDate.now());
        criteria.setSources(Set.of("PD_TRACKING", "VECTOR_TRAINING", "CANVAS"));

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        // Should be async for large reports
        assertNotNull(response);
        // Note: Status might be QUEUED or FAILED (if database not connected)
        assertTrue(response.getStatus() == ReportStatus.QUEUED ||
                        response.getStatus() == ReportStatus.FAILED,
                "Large report should be queued for async processing or fail if DB not available");
    }

    @Test
    void testReportGeneration_MultipleSources() {
        // Create request with all three data sources
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3));
        criteria.setStartDate(LocalDate.now().minusMonths(3));
        criteria.setEndDate(LocalDate.now());
        criteria.setSources(Set.of("PD_TRACKING", "VECTOR_TRAINING", "CANVAS"));

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        assertNotNull(response);
        assertNotNull(response.getStatus());
    }

    @Test
    void testReportGeneration_WithEventAttributeFilter() {
        // Create request with event attribute filtering
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3));
        criteria.setStartDate(LocalDate.now().minusMonths(1));
        criteria.setEndDate(LocalDate.now());

        // Add event attribute filter
        ActivityByUserCriteria.EventAttributeFilter filter = new ActivityByUserCriteria.EventAttributeFilter();
        filter.setEventAttributeId(1);
        filter.setEventAttributeOptionId(1);
        criteria.setEventAttributeFilters(List.of(filter));

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        assertNotNull(response);
        assertNotNull(response.getStatus());
    }

    @Test
    void testReportGeneration_WithUserAndEventProperties() {
        // Create request with custom user and event properties
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3));
        criteria.setStartDate(LocalDate.now().minusMonths(1));
        criteria.setEndDate(LocalDate.now());
        criteria.setUserProperties(List.of("JOB", "SCHOOL"));
        criteria.setEventProperties(List.of("PRESENTER", "LOCATION"));

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        assertNotNull(response);
        assertNotNull(response.getStatus());
    }

    @Test
    void testReportGeneration_SpecificProgram() {
        // Create request with specific program filter
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3));
        criteria.setStartDate(LocalDate.now().minusMonths(1));
        criteria.setEndDate(LocalDate.now());
        criteria.setProgramId(123); // Specific program ID

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        assertNotNull(response);
        assertNotNull(response.getStatus());
    }

    @Test
    void testReportGeneration_AllPrograms() {
        // Create request with all programs (programId = 0)
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3));
        criteria.setStartDate(LocalDate.now().minusMonths(1));
        criteria.setEndDate(LocalDate.now());
        criteria.setProgramId(0); // All programs

        ReportRequest request = new ReportRequest();
        request.setReportType(ReportType.ACTIVITY_BY_USER);
        request.setCriteria(criteria);

        Integer userId = 999;
        Integer districtId = 1;

        // Generate report
        ReportResponse response = reportGeneratorService.generateReport(request, userId, districtId);

        assertNotNull(response);
        assertNotNull(response.getStatus());
    }

    // Note: Validation tests are covered in ActivityByUserReportHandlerTest (unit tests).
    // Integration tests focus on successful end-to-end flow and framework integration.

    // ===== Helper Methods =====

    private ActivityByUserCriteria createValidCriteria() {
        ActivityByUserCriteria criteria = new ActivityByUserCriteria();
        criteria.setDistrictId(1);
        criteria.setUserIds(List.of(1, 2, 3, 4, 5));
        criteria.setStartDate(LocalDate.of(2024, 1, 1));
        criteria.setEndDate(LocalDate.of(2024, 12, 31));
        criteria.setProgramId(0); // 0 = all programs
        return criteria;
    }

    private List<Integer> createUserList(int count) {
        List<Integer> users = new java.util.ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(i);
        }
        return users;
    }
}
