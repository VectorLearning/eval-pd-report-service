package com.evplus.report.service;

import com.evplus.report.model.entity.ThresholdConfig;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ThresholdConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ThresholdService.
 */
@ExtendWith(MockitoExtension.class)
class ThresholdServiceTest {

    @Mock
    private ThresholdConfigRepository thresholdConfigRepository;

    @InjectMocks
    private ThresholdService thresholdService;

    private ThresholdConfig userActivityConfig;

    @BeforeEach
    void setUp() {
        userActivityConfig = new ThresholdConfig();
        userActivityConfig.setReportType(ReportType.USER_ACTIVITY);
        userActivityConfig.setMaxRecords(5000);
        userActivityConfig.setMaxDurationSeconds(10);
        userActivityConfig.setDescription("User activity threshold");
    }

    @Test
    void testGetThresholdConfig_Found() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        ThresholdConfig result = thresholdService.getThresholdConfig(ReportType.USER_ACTIVITY);

        assertNotNull(result);
        assertEquals(ReportType.USER_ACTIVITY, result.getReportType());
        assertEquals(5000, result.getMaxRecords());
        assertEquals(10, result.getMaxDurationSeconds());

        verify(thresholdConfigRepository, times(1)).findById(ReportType.USER_ACTIVITY);
    }

    @Test
    void testGetThresholdConfig_NotFound_ReturnsDefault() {
        when(thresholdConfigRepository.findById(ReportType.DUMMY_TEST))
            .thenReturn(Optional.empty());

        ThresholdConfig result = thresholdService.getThresholdConfig(ReportType.DUMMY_TEST);

        assertNotNull(result);
        assertEquals(ReportType.DUMMY_TEST, result.getReportType());
        assertEquals(5000, result.getMaxRecords());
        assertEquals(10, result.getMaxDurationSeconds());
        assertEquals("Default threshold configuration", result.getDescription());

        verify(thresholdConfigRepository, times(1)).findById(ReportType.DUMMY_TEST);
    }

    @Test
    void testExceedsRecordThreshold_ExceedsLimit() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.exceedsRecordThreshold(ReportType.USER_ACTIVITY, 6000);

        assertTrue(result);
    }

    @Test
    void testExceedsRecordThreshold_WithinLimit() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.exceedsRecordThreshold(ReportType.USER_ACTIVITY, 3000);

        assertFalse(result);
    }

    @Test
    void testExceedsRecordThreshold_ExactlyAtLimit() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.exceedsRecordThreshold(ReportType.USER_ACTIVITY, 5000);

        assertFalse(result);
    }

    @Test
    void testExceedsDurationThreshold_ExceedsLimit() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.exceedsDurationThreshold(ReportType.USER_ACTIVITY, 15);

        assertTrue(result);
    }

    @Test
    void testExceedsDurationThreshold_WithinLimit() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.exceedsDurationThreshold(ReportType.USER_ACTIVITY, 5);

        assertFalse(result);
    }

    @Test
    void testShouldProcessAsync_BothWithinLimits() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.shouldProcessAsync(ReportType.USER_ACTIVITY, 3000, 5);

        assertFalse(result);
    }

    @Test
    void testShouldProcessAsync_RecordsExceed() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.shouldProcessAsync(ReportType.USER_ACTIVITY, 6000, 5);

        assertTrue(result);
    }

    @Test
    void testShouldProcessAsync_DurationExceeds() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.shouldProcessAsync(ReportType.USER_ACTIVITY, 3000, 15);

        assertTrue(result);
    }

    @Test
    void testShouldProcessAsync_BothExceed() {
        when(thresholdConfigRepository.findById(ReportType.USER_ACTIVITY))
            .thenReturn(Optional.of(userActivityConfig));

        boolean result = thresholdService.shouldProcessAsync(ReportType.USER_ACTIVITY, 10000, 20);

        assertTrue(result);
    }
}
