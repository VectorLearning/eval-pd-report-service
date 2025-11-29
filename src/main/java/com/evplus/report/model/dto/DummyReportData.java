package com.evplus.report.model.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * Report data for DUMMY_TEST report type.
 * Contains a list of dummy records for testing the async reporting pipeline.
 *
 * This data structure is used to validate:
 * - JSON serialization/deserialization
 * - Excel file generation (Apache POI)
 * - S3 upload and download
 * - Email notification delivery
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DummyReportData extends ReportData {

    /**
     * List of dummy records in the report.
     * Size can be configured via DummyReportCriteria.recordCount.
     */
    private List<DummyRecord> records;
}
