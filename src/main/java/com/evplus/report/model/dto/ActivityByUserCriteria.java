package com.evplus.report.model.dto;

import com.evplus.report.model.enums.ReportType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Criteria for ACTIVITY_BY_USER report type.
 * Professional development activities for users including PD events, online courses,
 * and credit hours earned from multiple sources.
 *
 * This report supports:
 * - Multiple data sources (PD Tracking, Vector Training, Canvas)
 * - Customizable user and event properties
 * - Event attribute filtering
 * - Date range and program filtering
 * - Credit type calculations
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityByUserCriteria extends ReportCriteria {

    /**
     * District ID (required).
     * Identifies the district for which the report is generated.
     */
    private Integer districtId;

    /**
     * ID of the user making the report request (optional, needed for MY_EVALUEES resolution).
     * When userIds contains -3 (MY_EVALUEES), this field is used to query permissions
     * and determine which users the requesting user can view.
     * This is set from the root-level userId in the ReportRequest.
     */
    private Integer requestingUserId;

    /**
     * List of user IDs to include in the report (required).
     * Empty list will result in no data.
     * Special values:
     * - -2: ALL_USERS_DISTRICT (all active users in the district)
     * - -3: MY_EVALUEES (users the requesting user has permission to view)
     */
    private List<Integer> userIds;

    /**
     * Report start date (required).
     * Activities on or after this date will be included.
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    /**
     * Report end date (required).
     * Activities on or before this date will be included.
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    /**
     * Program ID to filter by (required).
     * Use 0 or "ALL" to include all programs.
     */
    private Integer programId;

    /**
     * Additional user properties to include as columns (optional).
     * Examples: JOB, SCHOOL, DEPARTMENT, etc.
     * These will be added as additional columns in the output.
     */
    private List<String> userProperties;

    /**
     * Additional event properties to include as columns (optional).
     * Examples: EVENT, EVENT_START, PRESENTER, LOCATION, etc.
     * These will be added as additional columns in the output.
     */
    private List<String> eventProperties;

    /**
     * Event attribute options for filtering events (optional).
     * Only events matching these attributes will be included.
     * Format: attribute_id:option_id pairs
     */
    private List<EventAttributeFilter> eventAttributeFilters;

    /**
     * Data sources to include (optional).
     * Valid values: PD_TRACKING, VECTOR_TRAINING, CANVAS
     * Defaults to PD_TRACKING only if not specified.
     */
    private Set<String> sources;

    /**
     * Include users with no activity data (optional).
     * If true, users with no activities in the date range will be included with zero values.
     * Default: false
     */
    private boolean showUsersWithoutData = false;

    /**
     * User group filters for filtering users by organizational properties (optional).
     * Examples: schools, jobs, regions, departments, teams, etc.
     * When specified, only users matching these criteria will be included.
     *
     * Multiple filter groups can be specified:
     * - All filters within a single UserGroupFilter are AND-ed together
     * - Multiple UserGroupFilter objects are OR-ed together
     *
     * Example: "Users in (School 1 AND Job 5) OR (Region 10 AND Job 6)"
     * Can be combined with userIds selection (including MY_EVALUEES).
     */
    private List<UserGroupFilter> userGroupFilters;

    @Override
    public ReportType getReportType() {
        return ReportType.ACTIVITY_BY_USER;
    }

    /**
     * Inner class representing an event attribute filter.
     * Used to filter events by specific attribute options.
     */
    @Data
    public static class EventAttributeFilter {
        /**
         * Event attribute ID.
         */
        private Integer eventAttributeId;

        /**
         * Event attribute option ID.
         */
        private Integer eventAttributeOptionId;
    }
}
