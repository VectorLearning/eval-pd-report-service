package com.evplus.report.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Report data for ACTIVITY_BY_USER report type.
 * Contains professional development activity records for users including events attended,
 * courses completed, and credits earned.
 *
 * This data structure includes:
 * - List of activity records (one per event/course per user)
 * - Column headers (dynamic based on user/event properties selected)
 * - Credit type headers (dynamic based on credit types used in district)
 * - Summary totals by credit type
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityByUserReportData extends ReportData {

    /**
     * List of activity records in the report.
     * Each record represents one user's participation in one PD event or online course.
     */
    private List<ActivityRecord> records;

    /**
     * Dynamic column headers for the report.
     * Includes user properties, event properties, and fixed columns (Source, Program, Title, Date).
     * Order matches the data in ActivityRecord.columnValues.
     */
    private List<String> columnHeaders;

    /**
     * Credit type headers for credit columns.
     * Order matches the credit values in ActivityRecord.creditValues.
     * Example: ["Contact Hours", "CEUs", "Graduate Credits"]
     */
    private List<String> creditHeaders;

    /**
     * Total credits by credit type across all records.
     * Key: credit type name, Value: total credit value
     * Example: {"Contact Hours": 24.5, "CEUs": 2.45, "Graduate Credits": 0.0}
     */
    private Map<String, Float> totalCreditsByType;

    /**
     * Individual activity record representing one user's participation in a PD event or course.
     */
    @Data
    public static class ActivityRecord {
        /**
         * User ID.
         */
        private Integer userId;

        /**
         * User display name (first name + last name).
         */
        private String userName;

        /**
         * Event/course ID.
         */
        private Integer eventId;

        /**
         * Data source for this record.
         * Values: PD_TRACKING, VECTOR_TRAINING, CANVAS
         */
        private String source;

        /**
         * Program name (empty for non-PD Tracking sources).
         */
        private String programName;

        /**
         * Event/course title.
         */
        private String eventTitle;

        /**
         * Event start date or course completion date.
         */
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime eventDate;

        /**
         * Dynamic column values corresponding to columnHeaders.
         * Includes user property values, event property values, and fixed column values.
         * Order must match ActivityByUserReportData.columnHeaders.
         */
        private List<String> columnValues;

        /**
         * Credit values by credit type.
         * Order must match ActivityByUserReportData.creditHeaders.
         * Example: [12.0, 1.2, 0.0] for Contact Hours, CEUs, Graduate Credits
         */
        private List<Float> creditValues;

        /**
         * Total credits for this record (sum of all credit types).
         */
        private Float totalCredits;
    }
}
