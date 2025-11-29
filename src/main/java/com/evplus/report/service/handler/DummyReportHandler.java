package com.evplus.report.service.handler;

import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.DummyRecord;
import com.evplus.report.model.dto.DummyReportCriteria;
import com.evplus.report.model.dto.DummyReportData;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.enums.ReportType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Handler for DUMMY_TEST report type.
 * Used for testing the async reporting pipeline without real data dependencies.
 *
 * This handler:
 * - Auto-registers with HandlerRegistry on application startup
 * - Generates configurable test data (default 10,000 records)
 * - Always triggers async processing to validate async workflow
 * - Validates the complete pipeline: SQS → Process → Excel → S3 → Notification
 *
 * This handler remains in the codebase for testing purposes even after
 * other report types are implemented.
 */
@Slf4j
@Service
public class DummyReportHandler implements ReportHandler {

    @Override
    public ReportType getReportType() {
        return ReportType.DUMMY_TEST;
    }

    @Override
    public void validateCriteria(ReportCriteria criteria) throws ValidationException {
        if (!(criteria instanceof DummyReportCriteria)) {
            throw new ValidationException("Invalid criteria type for DUMMY_TEST report");
        }

        DummyReportCriteria dummyCriteria = (DummyReportCriteria) criteria;
        List<String> errors = new ArrayList<>();

        // Validate record count
        if (dummyCriteria.getRecordCount() < 0) {
            errors.add("Record count must be non-negative");
        }

        if (dummyCriteria.getRecordCount() > 1_000_000) {
            errors.add("Record count cannot exceed 1,000,000 for safety");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }

        log.debug("Validation passed for DUMMY_TEST report with {} records",
                dummyCriteria.getRecordCount());
    }

    @Override
    public boolean exceedsAsyncThreshold(ReportCriteria criteria) {
        // Always return true to test async flow
        // In real scenarios, this would check against ThresholdService
        log.debug("DUMMY_TEST report always triggers async processing for pipeline testing");
        return true;
    }

    @Override
    public ReportData generateReport(ReportCriteria criteria) {
        DummyReportCriteria dummyCriteria = (DummyReportCriteria) criteria;
        int recordCount = dummyCriteria.getRecordCount();

        log.info("Generating DUMMY_TEST report with {} records (testParameter: {})",
                recordCount, dummyCriteria.getTestParameter());

        long startTime = System.currentTimeMillis();

        // Generate realistic test data
        List<DummyRecord> records = IntStream.range(0, recordCount)
                .mapToObj(i -> new DummyRecord(
                        "Field1-" + i,
                        "Field2-" + i,
                        "Field3-" + i,
                        LocalDateTime.now().minusHours(i % 24).minusMinutes(i % 60)
                ))
                .collect(Collectors.toList());

        DummyReportData data = new DummyReportData();
        data.setRecords(records);
        data.setTotalRecords(records.size());
        data.setGeneratedAt(LocalDateTime.now());

        long duration = System.currentTimeMillis() - startTime;
        log.info("DUMMY_TEST report generation completed: {} records in {} ms",
                records.size(), duration);

        return data;
    }

    @Override
    public Class<? extends ReportCriteria> getCriteriaClass() {
        return DummyReportCriteria.class;
    }
}
