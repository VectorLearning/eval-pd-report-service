package com.evplus.report.service.handler;

import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.DummyRecord;
import com.evplus.report.model.dto.DummyReportCriteria;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.enums.ReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DummyReportHandler.
 * Validates all ReportHandler interface methods and test data generation.
 */
class DummyReportHandlerTest {

    private DummyReportHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DummyReportHandler();
    }

    @Test
    void testGetReportType() {
        assertEquals(ReportType.DUMMY_TEST, handler.getReportType());
    }

    @Test
    void testGetCriteriaClass() {
        assertEquals(DummyReportCriteria.class, handler.getCriteriaClass());
    }

    @Test
    void testExceedsAsyncThreshold_AlwaysReturnsTrue() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(100);

        // Should always return true to test async flow
        assertTrue(handler.exceedsAsyncThreshold(criteria));
    }

    @Test
    void testValidateCriteria_ValidCriteria() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(10000);
        criteria.setTestParameter("test-param");

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    @Test
    void testValidateCriteria_InvalidCriteriaType() {
        // Create a different criteria type
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
    void testValidateCriteria_NegativeRecordCount() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(-1);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("non-negative"));
    }

    @Test
    void testValidateCriteria_ExceedsMaximumRecordCount() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(1_500_000);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> handler.validateCriteria(criteria));

        assertTrue(exception.getMessage().contains("cannot exceed 1,000,000"));
    }

    @Test
    void testValidateCriteria_BoundaryValue_ZeroRecords() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(0);

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    @Test
    void testValidateCriteria_BoundaryValue_MaxRecords() {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(1_000_000);

        assertDoesNotThrow(() -> handler.validateCriteria(criteria));
    }

    @Test
    void testGenerateReport_DefaultRecordCount() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        // Default record count is 10,000

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        assertTrue(reportData instanceof DummyReportData);

        DummyReportData dummyData = (DummyReportData) reportData;
        assertNotNull(dummyData.getRecords());
        assertEquals(10000, dummyData.getRecords().size());
        assertEquals(10000, dummyData.getTotalRecords());
        assertNotNull(dummyData.getGeneratedAt());
    }

    @Test
    void testGenerateReport_SmallRecordCount() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(100);

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        DummyReportData dummyData = (DummyReportData) reportData;

        assertEquals(100, dummyData.getRecords().size());
        assertEquals(100, dummyData.getTotalRecords());
    }

    @Test
    void testGenerateReport_LargeRecordCount() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(100_000);

        long startTime = System.currentTimeMillis();
        ReportData reportData = handler.generateReport(criteria);
        long duration = System.currentTimeMillis() - startTime;

        assertNotNull(reportData);
        DummyReportData dummyData = (DummyReportData) reportData;

        assertEquals(100_000, dummyData.getRecords().size());
        assertEquals(100_000, dummyData.getTotalRecords());

        // Verify performance - should generate 100K records in reasonable time
        assertTrue(duration < 10000, "Report generation took too long: " + duration + "ms");
    }

    @Test
    void testGenerateReport_ZeroRecords() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(0);

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        DummyReportData dummyData = (DummyReportData) reportData;

        assertEquals(0, dummyData.getRecords().size());
        assertEquals(0, dummyData.getTotalRecords());
    }

    @Test
    void testGenerateReport_RecordDataStructure() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(10);
        criteria.setTestParameter("test-123");

        ReportData reportData = handler.generateReport(criteria);
        DummyReportData dummyData = (DummyReportData) reportData;

        List<DummyRecord> records = dummyData.getRecords();
        assertNotNull(records);
        assertEquals(10, records.size());

        // Verify record structure
        for (int i = 0; i < records.size(); i++) {
            DummyRecord record = records.get(i);
            assertNotNull(record);
            assertEquals("Field1-" + i, record.getField1());
            assertEquals("Field2-" + i, record.getField2());
            assertEquals("Field3-" + i, record.getField3());
            assertNotNull(record.getTimestamp());
            assertTrue(record.getTimestamp().isBefore(LocalDateTime.now().plusMinutes(1)));
        }
    }

    @Test
    void testGenerateReport_WithTestParameter() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(5);
        criteria.setTestParameter("custom-test-param");

        ReportData reportData = handler.generateReport(criteria);

        assertNotNull(reportData);
        DummyReportData dummyData = (DummyReportData) reportData;
        assertEquals(5, dummyData.getRecords().size());
    }

    @Test
    void testGenerateReport_TimestampVariation() throws Exception {
        DummyReportCriteria criteria = new DummyReportCriteria();
        criteria.setRecordCount(50);

        ReportData reportData = handler.generateReport(criteria);
        DummyReportData dummyData = (DummyReportData) reportData;

        List<DummyRecord> records = dummyData.getRecords();

        // Verify that timestamps vary (simulate different activity times)
        LocalDateTime firstTimestamp = records.get(0).getTimestamp();
        LocalDateTime lastTimestamp = records.get(records.size() - 1).getTimestamp();

        assertNotEquals(firstTimestamp, lastTimestamp,
                "Timestamps should vary across records");
    }
}
