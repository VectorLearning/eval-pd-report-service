package com.evplus.report.model.enums;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReportStatus enum.
 */
class ReportStatusTest {

    @Test
    void testGetValue() {
        assertEquals(0, ReportStatus.QUEUED.getValue());
        assertEquals(1, ReportStatus.PROCESSING.getValue());
        assertEquals(2, ReportStatus.COMPLETED.getValue());
        assertEquals(3, ReportStatus.FAILED.getValue());
    }

    @Test
    void testFromValue() {
        assertEquals(ReportStatus.QUEUED, ReportStatus.fromValue(0));
        assertEquals(ReportStatus.PROCESSING, ReportStatus.fromValue(1));
        assertEquals(ReportStatus.COMPLETED, ReportStatus.fromValue(2));
        assertEquals(ReportStatus.FAILED, ReportStatus.fromValue(3));
    }

    @Test
    void testFromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ReportStatus.fromValue(99));
        assertThrows(IllegalArgumentException.class, () -> ReportStatus.fromValue(-1));
    }

    @Test
    void testAllValuesUnique() {
        // Ensure all enum values have unique integer values
        assertEquals(4, ReportStatus.values().length);
        assertNotEquals(ReportStatus.QUEUED.getValue(), ReportStatus.PROCESSING.getValue());
        assertNotEquals(ReportStatus.PROCESSING.getValue(), ReportStatus.COMPLETED.getValue());
        assertNotEquals(ReportStatus.COMPLETED.getValue(), ReportStatus.FAILED.getValue());
    }
}
