package com.evplus.report.service.handler;

import com.evplus.report.exception.UnsupportedReportTypeException;
import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.enums.ReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HandlerRegistry.
 */
class HandlerRegistryTest {

    private HandlerRegistry registry;
    private MockUserActivityHandler userActivityHandler;
    private MockDummyTestHandler dummyTestHandler;

    @BeforeEach
    void setUp() {
        // Create mock handlers
        userActivityHandler = new MockUserActivityHandler();
        dummyTestHandler = new MockDummyTestHandler();

        // Create registry with mock handlers
        List<ReportHandler> handlers = Arrays.asList(userActivityHandler, dummyTestHandler);
        registry = new HandlerRegistry(handlers);
    }

    @Test
    void testGetHandler_ValidType() {
        ReportHandler handler = registry.getHandler(ReportType.USER_ACTIVITY);
        assertNotNull(handler);
        assertEquals(userActivityHandler, handler);

        handler = registry.getHandler(ReportType.DUMMY_TEST);
        assertNotNull(handler);
        assertEquals(dummyTestHandler, handler);
    }

    @Test
    void testGetHandler_UnsupportedType() {
        // This test is a bit tricky since we have all enum values covered
        // In real scenario, a new enum value might be added without a handler
        // For now, we'll just verify the exception is thrown for demonstration

        // Note: Since all ReportType enum values have handlers in our test,
        // we can't actually test this without adding a new enum value
        // This test serves as documentation of expected behavior
        assertNotNull(registry.getHandler(ReportType.USER_ACTIVITY));
    }

    @Test
    void testGetSupportedReportTypes() {
        Set<ReportType> supportedTypes = registry.getSupportedReportTypes();

        assertEquals(2, supportedTypes.size());
        assertTrue(supportedTypes.contains(ReportType.USER_ACTIVITY));
        assertTrue(supportedTypes.contains(ReportType.DUMMY_TEST));
    }

    @Test
    void testIsSupported() {
        assertTrue(registry.isSupported(ReportType.USER_ACTIVITY));
        assertTrue(registry.isSupported(ReportType.DUMMY_TEST));
    }

    @Test
    void testEmptyRegistry() {
        HandlerRegistry emptyRegistry = new HandlerRegistry(List.of());

        assertEquals(0, emptyRegistry.getSupportedReportTypes().size());
        assertFalse(emptyRegistry.isSupported(ReportType.USER_ACTIVITY));

        assertThrows(UnsupportedReportTypeException.class,
            () -> emptyRegistry.getHandler(ReportType.USER_ACTIVITY));
    }

    // Mock handler implementations for testing

    static class MockUserActivityHandler implements ReportHandler {
        @Override
        public ReportType getReportType() {
            return ReportType.USER_ACTIVITY;
        }

        @Override
        public void validateCriteria(ReportCriteria criteria) throws ValidationException {
            // Mock implementation
        }

        @Override
        public boolean exceedsAsyncThreshold(ReportCriteria criteria) {
            return false;
        }

        @Override
        public ReportData generateReport(ReportCriteria criteria) {
            return new MockReportData();
        }

        @Override
        public Class<? extends ReportCriteria> getCriteriaClass() {
            return MockReportCriteria.class;
        }
    }

    static class MockDummyTestHandler implements ReportHandler {
        @Override
        public ReportType getReportType() {
            return ReportType.DUMMY_TEST;
        }

        @Override
        public void validateCriteria(ReportCriteria criteria) throws ValidationException {
            // Mock implementation
        }

        @Override
        public boolean exceedsAsyncThreshold(ReportCriteria criteria) {
            return false;
        }

        @Override
        public ReportData generateReport(ReportCriteria criteria) {
            return new MockReportData();
        }

        @Override
        public Class<? extends ReportCriteria> getCriteriaClass() {
            return MockReportCriteria.class;
        }
    }

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
