package com.evplus.report.model.dto;

import com.evplus.report.model.enums.ReportType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Criteria for DUMMY_TEST report type.
 * Used for testing async pipeline infrastructure without real data dependencies.
 *
 * This criteria allows configurable test data generation to validate the complete
 * async workflow (SQS → Process → Excel → S3 → Notification) with various data volumes.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DummyReportCriteria extends ReportCriteria {

    /**
     * Optional test parameter for demonstration purposes.
     * Not used in actual report generation but can be useful for testing parameter serialization.
     */
    private String testParameter;

    /**
     * Number of test records to generate.
     * Default: 10,000 records for realistic async testing.
     * Can be configured for different test scenarios:
     * - 100 for quick sync tests
     * - 1,000 for small async tests
     * - 10,000 for realistic async tests
     * - 100,000 for stress testing
     */
    private int recordCount = 10000;

    @Override
    public ReportType getReportType() {
        return ReportType.DUMMY_TEST;
    }
}
