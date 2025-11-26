package com.evplus.report.service.handler;

import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.enums.ReportType;

/**
 * Interface that all report handlers must implement.
 * Defines the contract for report generation logic.
 *
 * Each report type (e.g., USER_ACTIVITY, DUMMY_TEST) should have its own
 * implementation of this interface, which will be auto-registered by the
 * HandlerRegistry using Spring's dependency injection.
 *
 * Implementations should be annotated with @Component or @Service.
 */
public interface ReportHandler {

    /**
     * Get the report type this handler supports.
     * Used by HandlerRegistry for routing requests to the correct handler.
     *
     * @return the ReportType enum value this handler processes
     */
    ReportType getReportType();

    /**
     * Validate the report criteria before processing.
     * Implementations should check all required fields and business rules.
     *
     * @param criteria the report criteria to validate
     * @throws ValidationException if validation fails, with detailed error messages
     */
    void validateCriteria(ReportCriteria criteria) throws ValidationException;

    /**
     * Determine if this report request exceeds the async threshold.
     * Implementations should estimate the number of records and/or duration
     * and compare against threshold configuration.
     *
     * @param criteria the report criteria to evaluate
     * @return true if the report should be processed asynchronously, false for sync
     */
    boolean exceedsAsyncThreshold(ReportCriteria criteria);

    /**
     * Generate the report data.
     * This is the core logic for producing the report.
     *
     * For sync reports: This method is called directly and returns immediately.
     * For async reports: This method is called from SQS listener in background.
     *
     * @param criteria the validated report criteria
     * @return the generated report data
     * @throws Exception if report generation fails
     */
    ReportData generateReport(ReportCriteria criteria) throws Exception;

    /**
     * Get the criteria class type for this handler.
     * Used for deserialization of JSON criteria from database/SQS messages.
     *
     * @return the Class object for this handler's criteria type
     */
    Class<? extends ReportCriteria> getCriteriaClass();
}
