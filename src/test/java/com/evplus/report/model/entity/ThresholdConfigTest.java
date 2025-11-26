package com.evplus.report.model.entity;

import com.evplus.report.model.enums.ReportType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ThresholdConfig entity.
 */
class ThresholdConfigTest {

    @Test
    void testDefaultValues() {
        ThresholdConfig config = new ThresholdConfig();
        assertEquals(5000, config.getMaxRecords());
        assertEquals(10, config.getMaxDurationSeconds());
    }

    @Test
    void testOnCreateCallback() {
        ThresholdConfig config = new ThresholdConfig();
        config.onCreate();

        assertNotNull(config.getUpdatedAt());
    }

    @Test
    void testOnUpdateCallback() throws InterruptedException {
        ThresholdConfig config = new ThresholdConfig();
        config.onCreate();
        LocalDateTime initialUpdatedAt = config.getUpdatedAt();

        Thread.sleep(10); // Small delay to ensure timestamp difference
        config.onUpdate();

        assertTrue(config.getUpdatedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void testEntityConstruction() {
        ThresholdConfig config = new ThresholdConfig();
        config.setReportType(ReportType.USER_ACTIVITY);
        config.setMaxRecords(10000);
        config.setMaxDurationSeconds(30);
        config.setDescription("Custom threshold config");

        assertEquals(ReportType.USER_ACTIVITY, config.getReportType());
        assertEquals(10000, config.getMaxRecords());
        assertEquals(30, config.getMaxDurationSeconds());
        assertEquals("Custom threshold config", config.getDescription());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();
        ThresholdConfig config = new ThresholdConfig(
            ReportType.DUMMY_TEST,
            8000,
            20,
            "Test config",
            now
        );

        assertEquals(ReportType.DUMMY_TEST, config.getReportType());
        assertEquals(8000, config.getMaxRecords());
        assertEquals(20, config.getMaxDurationSeconds());
        assertEquals("Test config", config.getDescription());
        assertEquals(now, config.getUpdatedAt());
    }
}
