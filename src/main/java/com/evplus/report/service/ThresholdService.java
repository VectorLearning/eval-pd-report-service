package com.evplus.report.service;

import com.evplus.report.model.entity.ThresholdConfig;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.repository.ThresholdConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service for managing report threshold configuration.
 * Determines whether a report should be processed synchronously or asynchronously
 * based on configurable thresholds (max records, max duration).
 *
 * Threshold configurations are cached using Spring Cache (Redis) to avoid
 * repeated database queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThresholdService {

    private final ThresholdConfigRepository thresholdConfigRepository;

    /**
     * Get threshold configuration for a specific report type.
     * Results are cached in Redis with key: "threshold::{reportType}".
     *
     * @param reportType the report type to get configuration for
     * @return the threshold configuration, or default values if not found
     */
    @Cacheable(value = "threshold", key = "#reportType")
    public ThresholdConfig getThresholdConfig(ReportType reportType) {
        log.debug("Loading threshold config for report type: {}", reportType);

        return thresholdConfigRepository.findById(reportType)
            .orElseGet(() -> {
                log.warn("No threshold config found for report type: {}. Using defaults.", reportType);
                return createDefaultConfig(reportType);
            });
    }

    /**
     * Check if the estimated record count exceeds the async threshold.
     *
     * @param reportType the report type
     * @param estimatedRecordCount the estimated number of records
     * @return true if async processing is required, false for sync
     */
    public boolean exceedsRecordThreshold(ReportType reportType, int estimatedRecordCount) {
        ThresholdConfig config = getThresholdConfig(reportType);
        boolean exceeds = estimatedRecordCount > config.getMaxRecords();

        log.debug("Report type: {}, Estimated records: {}, Max records: {}, Exceeds: {}",
            reportType, estimatedRecordCount, config.getMaxRecords(), exceeds);

        return exceeds;
    }

    /**
     * Check if the estimated duration exceeds the async threshold.
     *
     * @param reportType the report type
     * @param estimatedDurationSeconds the estimated processing time in seconds
     * @return true if async processing is required, false for sync
     */
    public boolean exceedsDurationThreshold(ReportType reportType, int estimatedDurationSeconds) {
        ThresholdConfig config = getThresholdConfig(reportType);
        boolean exceeds = estimatedDurationSeconds > config.getMaxDurationSeconds();

        log.debug("Report type: {}, Estimated duration: {}s, Max duration: {}s, Exceeds: {}",
            reportType, estimatedDurationSeconds, config.getMaxDurationSeconds(), exceeds);

        return exceeds;
    }

    /**
     * Determine if async processing is required based on either threshold.
     * If either record count OR duration exceeds threshold, use async.
     *
     * @param reportType the report type
     * @param estimatedRecordCount estimated number of records
     * @param estimatedDurationSeconds estimated processing time in seconds
     * @return true if async processing is required, false for sync
     */
    public boolean shouldProcessAsync(ReportType reportType,
                                     int estimatedRecordCount,
                                     int estimatedDurationSeconds) {
        boolean recordsExceed = exceedsRecordThreshold(reportType, estimatedRecordCount);
        boolean durationExceeds = exceedsDurationThreshold(reportType, estimatedDurationSeconds);

        boolean async = recordsExceed || durationExceeds;

        log.info("Report type: {}, Records: {}, Duration: {}s, Process async: {}",
            reportType, estimatedRecordCount, estimatedDurationSeconds, async);

        return async;
    }

    /**
     * Create default threshold configuration.
     * Used when no configuration exists in database.
     *
     * @param reportType the report type
     * @return default threshold configuration
     */
    private ThresholdConfig createDefaultConfig(ReportType reportType) {
        ThresholdConfig config = new ThresholdConfig();
        config.setReportType(reportType);
        config.setMaxRecords(5000);
        config.setMaxDurationSeconds(10);
        config.setDescription("Default threshold configuration");
        return config;
    }
}
