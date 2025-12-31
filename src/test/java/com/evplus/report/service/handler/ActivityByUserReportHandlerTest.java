package com.evplus.report.service.handler;

import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.ActivityByUserCriteria;
import com.evplus.report.model.dto.ActivityByUserReportData;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.service.ThresholdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ActivityByUserReportHandler.
 * Tests validation logic, async threshold calculation, and report generation with mocked database.
 */
@ExtendWith(MockitoExtension.class)
class ActivityByUserReportHandlerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ThresholdService thresholdService;

    @Mock
    private com.evplus.report.service.UserSelectionService userSelectionService;

    private ActivityByUserReportHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ActivityByUserReportHandler(jdbcTemplate, thresholdService, userSelectionService);
    }

    // ===== Basic Interface Method Tests =====

    @Test
    void testGetReportType() {
        assertEquals(ReportType.ACTIVITY_BY_USER, handler.getReportType());
    }

    @Test
    void testGetCriteriaClass() {
        assertEquals(ActivityByUserCriteria.class, handler.getCriteriaClass());
    }

    // ===== Validation Tests =====

    @Test
    void testValidateCriteria_ValidCriteria() {
        ActivityByUserCriteria criteria = createValidCriteria();

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    @Test
    void testValidateCriteria_InvalidCriteriaType() {
        ReportCriteria invalidCriteria = new ReportCriteria() {
            @Override
            public ReportType getReportType() {
                return ReportType.USER_ACTIVITY;
            }
        };

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(invalidCriteria));

        assertTrue(exception.getMessage().contains("Invalid criteria type"));
    }

    @Test
    void testValidateCriteria_MissingDistrictId() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setDistrictId(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("District ID is required"));
    }

    @Test
    void testValidateCriteria_InvalidDistrictId() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setDistrictId(-1);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("District ID is required and must be positive"));
    }

    @Test
    void testValidateCriteria_MissingUserIds() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("At least one user ID is required"));
    }

    @Test
    void testValidateCriteria_EmptyUserIds() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(new ArrayList<>());

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("At least one user ID is required"));
    }

    @Test
    void testValidateCriteria_MissingStartDate() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setStartDate(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Start date is required"));
    }

    @Test
    void testValidateCriteria_MissingEndDate() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setEndDate(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("End date is required"));
    }

    @Test
    void testValidateCriteria_StartDateAfterEndDate() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setStartDate(LocalDate.of(2024, 12, 31));
        criteria.setEndDate(LocalDate.of(2024, 1, 1));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Start date must be before or equal to end date"));
    }

    @Test
    void testValidateCriteria_DateRangeExceedsTwoYears() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setStartDate(LocalDate.of(2022, 1, 1));
        criteria.setEndDate(LocalDate.of(2024, 12, 31)); // 3 years

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Date range cannot exceed 2 years"));
    }

    @Test
    void testValidateCriteria_DateRangeExactlyTwoYears() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setStartDate(LocalDate.of(2022, 1, 1));
        criteria.setEndDate(LocalDate.of(2024, 1, 1)); // Exactly 2 years

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    @Test
    void testValidateCriteria_MissingProgramId() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setProgramId(null);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Program ID is required"));
    }

    @Test
    void testValidateCriteria_InvalidSource() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setSources(Set.of("INVALID_SOURCE"));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Invalid source: INVALID_SOURCE"));
    }

    @Test
    void testValidateCriteria_ValidSources() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setSources(Set.of("PD_TRACKING", "VECTOR_TRAINING", "CANVAS"));

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    @Test
    void testValidateCriteria_InvalidEventAttributeFilter_MissingAttributeId() {
        ActivityByUserCriteria criteria = createValidCriteria();
        ActivityByUserCriteria.EventAttributeFilter filter = new ActivityByUserCriteria.EventAttributeFilter();
        filter.setEventAttributeId(null);
        filter.setEventAttributeOptionId(1);
        criteria.setEventAttributeFilters(List.of(filter));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Event attribute ID must be positive"));
    }

    @Test
    void testValidateCriteria_InvalidEventAttributeFilter_InvalidOptionId() {
        ActivityByUserCriteria criteria = createValidCriteria();
        ActivityByUserCriteria.EventAttributeFilter filter = new ActivityByUserCriteria.EventAttributeFilter();
        filter.setEventAttributeId(1);
        filter.setEventAttributeOptionId(-1);
        criteria.setEventAttributeFilters(List.of(filter));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("Event attribute option ID must be positive"));
    }

    @Test
    void testValidateCriteria_ValidEventAttributeFilter() {
        ActivityByUserCriteria criteria = createValidCriteria();
        ActivityByUserCriteria.EventAttributeFilter filter = new ActivityByUserCriteria.EventAttributeFilter();
        filter.setEventAttributeId(1);
        filter.setEventAttributeOptionId(1);
        criteria.setEventAttributeFilters(List.of(filter));

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    // ===== Async Threshold Tests =====

    @Test
    void testExceedsAsyncThreshold_SmallReport_ShouldBeSync() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3, 4, 5)); // 5 users
        criteria.setStartDate(LocalDate.now().minusDays(30)); // 30 days
        criteria.setEndDate(LocalDate.now());
        criteria.setSources(Set.of("PD_TRACKING")); // 1 source

        // Mock ThresholdService to return false for small reports
        when(thresholdService.shouldProcessAsync(eq(ReportType.ACTIVITY_BY_USER), anyInt(), anyInt()))
                .thenReturn(false);

        boolean result = handler.exceedsAsyncThreshold(criteria);

        assertFalse(result);
        verify(thresholdService).shouldProcessAsync(eq(ReportType.ACTIVITY_BY_USER), anyInt(), anyInt());
    }

    @Test
    void testExceedsAsyncThreshold_LargeReport_ShouldBeAsync() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(createUserList(1000)); // 1000 users
        criteria.setStartDate(LocalDate.now().minusYears(1)); // 1 year
        criteria.setEndDate(LocalDate.now());
        criteria.setSources(Set.of("PD_TRACKING", "VECTOR_TRAINING", "CANVAS")); // 3 sources

        // Mock ThresholdService to return true for large reports
        when(thresholdService.shouldProcessAsync(eq(ReportType.ACTIVITY_BY_USER), anyInt(), anyInt()))
                .thenReturn(true);

        boolean result = handler.exceedsAsyncThreshold(criteria);

        assertTrue(result);
        verify(thresholdService).shouldProcessAsync(eq(ReportType.ACTIVITY_BY_USER), anyInt(), anyInt());
    }

    @Test
    void testExceedsAsyncThreshold_DefaultSourceUsed() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setUserIds(List.of(1, 2, 3));
        criteria.setStartDate(LocalDate.now().minusMonths(3));
        criteria.setEndDate(LocalDate.now());
        criteria.setSources(null); // No sources specified, should default to PD_TRACKING

        when(thresholdService.shouldProcessAsync(eq(ReportType.ACTIVITY_BY_USER), anyInt(), anyInt()))
                .thenReturn(false);

        handler.exceedsAsyncThreshold(criteria);

        // Verify that estimation was done (sourceCount should be 1 for default)
        verify(thresholdService).shouldProcessAsync(eq(ReportType.ACTIVITY_BY_USER), anyInt(), anyInt());
    }

    // ===== Report Generation Tests (Mocked Database) =====

    @Test
    void testGenerateReport_PDTrackingOnly_Success() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setSources(Set.of("PD_TRACKING"));

        // Mock credit types query
        when(jdbcTemplate.queryForList(contains("pd_credit_types"), eq(String.class), eq(1)))
                .thenReturn(List.of("Contact Hours", "CEUs"));

        // Mock PD Tracking query - return empty for simplicity
        doAnswer(invocation -> null).when(jdbcTemplate)
                .query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        assertTrue(reportData instanceof ActivityByUserReportData);

        ActivityByUserReportData activityData = (ActivityByUserReportData) reportData;
        assertNotNull(activityData.getRecords());
        assertNotNull(activityData.getCreditHeaders());
        assertNotNull(activityData.getTotalCreditsByType());
        assertEquals(0, activityData.getTotalRecords()); // No records mocked
    }

    @Test
    void testGenerateReport_AllSources_Success() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setSources(Set.of("PD_TRACKING", "VECTOR_TRAINING", "CANVAS"));

        // Mock credit types query
        when(jdbcTemplate.queryForList(contains("pd_credit_types"), eq(String.class), eq(1)))
                .thenReturn(List.of("Contact Hours"));

        // Mock all source queries - return empty for simplicity
        doAnswer(invocation -> null).when(jdbcTemplate)
                .query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        assertTrue(reportData instanceof ActivityByUserReportData);

        ActivityByUserReportData activityData = (ActivityByUserReportData) reportData;
        assertNotNull(activityData.getColumnHeaders());

        // Verify all 3 sources were queried (plus credit types query = 4 total query calls)
        // Each source queries once per credit type (1 credit type * 3 sources = 3 queries)
        verify(jdbcTemplate, atLeast(3)).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));
    }

    @Test
    void testGenerateReport_DefaultSource_PDTrackingUsed() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setSources(null); // Should default to PD_TRACKING

        when(jdbcTemplate.queryForList(contains("pd_credit_types"), eq(String.class), eq(1)))
                .thenReturn(List.of("Contact Hours"));

        doAnswer(invocation -> null).when(jdbcTemplate)
                .query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        // Verify that at least one query was made (PD Tracking)
        verify(jdbcTemplate, atLeastOnce()).query(anyString(), any(Object[].class), any(RowCallbackHandler.class));
    }

    @Test
    void testGenerateReport_CreditTypesFallback_OnError() {
        ActivityByUserCriteria criteria = createValidCriteria();
        criteria.setSources(Set.of("PD_TRACKING"));

        // Mock credit types query to throw exception
        when(jdbcTemplate.queryForList(contains("pd_credit_types"), eq(String.class), eq(1)))
                .thenThrow(new RuntimeException("Database error"));

        // Mock PD Tracking query
        doAnswer(invocation -> null).when(jdbcTemplate)
                .query(anyString(), any(Object[].class), any(RowCallbackHandler.class));

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        ActivityByUserReportData activityData = (ActivityByUserReportData) reportData;

        // Should have fallen back to default "Contact Hours"
        assertNotNull(activityData.getCreditHeaders());
        assertEquals(1, activityData.getCreditHeaders().size());
        assertEquals("Contact Hours", activityData.getCreditHeaders().get(0));
    }

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
        List<Integer> users = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            users.add(i);
        }
        return users;
    }
}
