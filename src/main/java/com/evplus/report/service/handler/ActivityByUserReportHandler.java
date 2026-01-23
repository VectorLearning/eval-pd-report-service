package com.evplus.report.service.handler;

import com.evplus.report.exception.ValidationException;
import com.evplus.report.model.dto.ActivityByUserCriteria;
import com.evplus.report.model.dto.ActivityByUserReportData;
import com.evplus.report.model.dto.ReportCriteria;
import com.evplus.report.model.dto.ReportData;
import com.evplus.report.model.enums.EventProperty;
import com.evplus.report.model.enums.ReportType;
import com.evplus.report.model.enums.UserProperty;
import com.evplus.report.service.ThresholdService;
import com.evplus.report.service.UserSelectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Handler for ACTIVITY_BY_USER report type.
 * Generates professional development activity reports for users including:
 * - PD Tracking events (pd_advanced_events, pd_slot_attendances)
 * - Vector Training courses (SafeSchools)
 * - Canvas LMS courses
 *
 * This handler:
 * - Validates criteria (required fields, date ranges)
 * - Estimates record count for async threshold determination
 * - Executes complex multi-source database queries
 * - Aggregates credit information across multiple credit types
 * - Supports customizable user and event properties
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityByUserReportHandler implements ReportHandler {

    private final JdbcTemplate jdbcTemplate;
    private final ThresholdService thresholdService;
    private final UserSelectionService userSelectionService;

    // Data source constants
    private static final String SOURCE_PD_TRACKING = "PD_TRACKING";
    private static final String SOURCE_VECTOR_TRAINING = "VECTOR_TRAINING";
    private static final String SOURCE_CANVAS = "CANVAS";

    /**
     * Simple class to hold credit type ID and name
     */
    private static class CreditType {
        final Integer id;
        final String name;

        CreditType(Integer id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Override
    public ReportType getReportType() {
        return ReportType.ACTIVITY_BY_USER;
    }

    @Override
    public void validateCriteria(ReportCriteria criteria) throws ValidationException {
        if (!(criteria instanceof ActivityByUserCriteria)) {
            throw new ValidationException("Invalid criteria type for ACTIVITY_BY_USER report");
        }

        ActivityByUserCriteria activityCriteria = (ActivityByUserCriteria) criteria;
        List<String> errors = new ArrayList<>();

        // Validate required fields
        if (activityCriteria.getDistrictId() == null || activityCriteria.getDistrictId() <= 0) {
            errors.add("District ID is required and must be positive");
        }

        if (activityCriteria.getUserIds() == null || activityCriteria.getUserIds().isEmpty()) {
            errors.add("At least one user ID is required");
        }

        if (activityCriteria.getStartDate() == null) {
            errors.add("Start date is required");
        }

        if (activityCriteria.getEndDate() == null) {
            errors.add("End date is required");
        }

        // Validate date range
        if (activityCriteria.getStartDate() != null && activityCriteria.getEndDate() != null) {
            if (activityCriteria.getStartDate().isAfter(activityCriteria.getEndDate())) {
                errors.add("Start date must be before or equal to end date");
            }

            // Check for reasonable date range (max 2 years)
            long daysBetween = ChronoUnit.DAYS.between(
                    activityCriteria.getStartDate(),
                    activityCriteria.getEndDate()
            );
            if (daysBetween > 730) { // 2 years
                errors.add("Date range cannot exceed 2 years (730 days)");
            }
        }

        // Validate program ID
        if (activityCriteria.getProgramId() == null) {
            errors.add("Program ID is required (use 0 for all programs)");
        }

        // Validate sources if specified
        if (activityCriteria.getSources() != null && !activityCriteria.getSources().isEmpty()) {
            Set<String> validSources = Set.of(SOURCE_PD_TRACKING, SOURCE_VECTOR_TRAINING, SOURCE_CANVAS);
            for (String source : activityCriteria.getSources()) {
                if (!validSources.contains(source)) {
                    errors.add("Invalid source: " + source + ". Valid sources are: " +
                            String.join(", ", validSources));
                }
            }
        }

        // Validate event attribute filters if specified
        if (activityCriteria.getEventAttributeFilters() != null) {
            for (ActivityByUserCriteria.EventAttributeFilter filter : activityCriteria.getEventAttributeFilters()) {
                if (filter.getEventAttributeId() == null || filter.getEventAttributeId() <= 0) {
                    errors.add("Event attribute ID must be positive");
                }
                if (filter.getEventAttributeOptionId() == null || filter.getEventAttributeOptionId() <= 0) {
                    errors.add("Event attribute option ID must be positive");
                }
            }
        }

        // Validate user properties if specified
        if (activityCriteria.getUserProperties() != null && !activityCriteria.getUserProperties().isEmpty()) {
            for (String property : activityCriteria.getUserProperties()) {
                if (!UserProperty.isValid(property)) {
                    errors.add("Invalid user property: " + property + ". Valid properties: " +
                            Arrays.toString(UserProperty.values()));
                }
            }
        }

        // Validate event properties if specified
        if (activityCriteria.getEventProperties() != null && !activityCriteria.getEventProperties().isEmpty()) {
            for (String property : activityCriteria.getEventProperties()) {
                if (!EventProperty.isValid(property)) {
                    errors.add("Invalid event property: " + property + ". Valid properties: " +
                            Arrays.toString(EventProperty.values()));
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(String.join(", ", errors));
        }

        log.debug("Validation passed for ACTIVITY_BY_USER report: districtId={}, userCount={}, dateRange={} to {}",
                activityCriteria.getDistrictId(),
                activityCriteria.getUserIds().size(),
                activityCriteria.getStartDate(),
                activityCriteria.getEndDate());
    }

    @Override
    public boolean exceedsAsyncThreshold(ReportCriteria criteria) {
        ActivityByUserCriteria activityCriteria = (ActivityByUserCriteria) criteria;

        // TODO: Implement proper sync/async threshold logic
        // For now, ALWAYS use async processing for ACTIVITY_BY_USER reports
        // Sync flow will be implemented later with established logic

        int userCount = activityCriteria.getUserIds().size();
        long daysBetween = ChronoUnit.DAYS.between(
                activityCriteria.getStartDate(),
                activityCriteria.getEndDate()
        );

        Set<String> sources = activityCriteria.getSources();
        if (sources == null || sources.isEmpty()) {
            sources = Set.of(SOURCE_PD_TRACKING);
        }
        int sourceCount = sources.size();

        log.info("ACTIVITY_BY_USER report - FORCING ASYNC PROCESSING: users={}, days={}, sources={}",
                userCount, daysBetween, sourceCount);

        // Always return true to force async processing
        return true;
    }

    @Override
    public ReportData generateReport(ReportCriteria criteria) {
        ActivityByUserCriteria activityCriteria = (ActivityByUserCriteria) criteria;

        log.info("Generating ACTIVITY_BY_USER report: districtId={}, userIds={}, dateRange={} to {}",
                activityCriteria.getDistrictId(),
                userSelectionService.getSelectionDescription(activityCriteria.getUserIds()),
                activityCriteria.getStartDate(),
                activityCriteria.getEndDate());

        long startTime = System.currentTimeMillis();

        try {
            // Resolve special user selection values (-2, -3) to actual user IDs
            // and apply user group filters if provided.
            // This is done here (not at controller level) to avoid storing thousands
            // of user IDs in the job queue/database
            boolean hasSpecialSelection = userSelectionService.containsSpecialSelection(activityCriteria.getUserIds());
            boolean hasUserGroupFilters = activityCriteria.getUserGroupFilters() != null
                    && !activityCriteria.getUserGroupFilters().isEmpty()
                    && activityCriteria.getUserGroupFilters().stream()
                            .anyMatch(filter -> filter != null && !filter.isEmpty());

            if (hasSpecialSelection || hasUserGroupFilters) {
                String filterDescription = hasUserGroupFilters
                        ? activityCriteria.getUserGroupFilters().size() + " filter group(s)"
                        : "None";
                log.info("Resolving user selection: {}, User group filters: {}",
                        userSelectionService.getSelectionDescription(activityCriteria.getUserIds()),
                        filterDescription);

                List<Integer> resolvedUserIds = userSelectionService.resolveUserIdsWithFilter(
                        activityCriteria.getDistrictId(),
                        activityCriteria.getRequestingUserId(),
                        activityCriteria.getUserIds(),
                        activityCriteria.getUserGroupFilters()
                );

                log.info("Resolved to {} users", resolvedUserIds.size());
                activityCriteria.setUserIds(resolvedUserIds);

                // If no users found, return empty report
                if (resolvedUserIds.isEmpty()) {
                    log.warn("No users found for selection. Returning empty report.");
                    ActivityByUserReportData emptyReport = new ActivityByUserReportData();
                    emptyReport.setRecords(new ArrayList<>());
                    emptyReport.setTotalRecords(0);
                    emptyReport.setGeneratedAt(LocalDateTime.now());
                    return emptyReport;
                }
            }

            // Initialize report data
            ActivityByUserReportData reportData = new ActivityByUserReportData();
            List<ActivityByUserReportData.ActivityRecord> allRecords = new ArrayList<>();

            // Determine which sources to query
            Set<String> sources = activityCriteria.getSources();
            if (sources == null || sources.isEmpty()) {
                sources = Set.of(SOURCE_PD_TRACKING);
            }

            // Fetch credit types for this district
            List<CreditType> creditTypes = fetchCreditTypes(activityCriteria.getDistrictId());
            List<String> creditTypeNames = creditTypes.stream()
                    .map(ct -> ct.name)
                    .collect(java.util.stream.Collectors.toList());
            reportData.setCreditHeaders(creditTypeNames);

            // Build dynamic column headers
            List<String> columnHeaders = buildColumnHeaders(activityCriteria, sources.size() > 1);
            reportData.setColumnHeaders(columnHeaders);

            // Query each data source
            if (sources.contains(SOURCE_PD_TRACKING)) {
                log.debug("Querying PD_TRACKING data source");
                List<ActivityByUserReportData.ActivityRecord> pdRecords =
                        queryPDTrackingSource(activityCriteria, creditTypes);
                allRecords.addAll(pdRecords);
            }

            if (sources.contains(SOURCE_VECTOR_TRAINING)) {
                log.debug("Querying VECTOR_TRAINING data source");
                List<ActivityByUserReportData.ActivityRecord> vectorRecords =
                        queryVectorTrainingSource(activityCriteria, creditTypes);
                allRecords.addAll(vectorRecords);
            }

            if (sources.contains(SOURCE_CANVAS)) {
                log.debug("Querying CANVAS data source");
                List<ActivityByUserReportData.ActivityRecord> canvasRecords =
                        queryCanvasSource(activityCriteria, creditTypes);
                allRecords.addAll(canvasRecords);
            }

            // Sort records by user name and event date
            allRecords.sort(Comparator
                    .comparing(ActivityByUserReportData.ActivityRecord::getUserName)
                    .thenComparing(ActivityByUserReportData.ActivityRecord::getEventDate));

            // Calculate totals by credit type
            Map<String, Float> totalsByType = calculateCreditTotals(allRecords, creditTypes);

            // Set report data
            reportData.setRecords(allRecords);
            reportData.setTotalCreditsByType(totalsByType);
            reportData.setTotalRecords(allRecords.size());

            long duration = System.currentTimeMillis() - startTime;
            log.info("ACTIVITY_BY_USER report generation completed: {} records in {} ms",
                    allRecords.size(), duration);

            return reportData;

        } catch (Exception e) {
            log.error("Error generating ACTIVITY_BY_USER report", e);
            throw new RuntimeException("Failed to generate Activity By User report: " + e.getMessage(), e);
        }
    }

    @Override
    public Class<? extends ReportCriteria> getCriteriaClass() {
        return ActivityByUserCriteria.class;
    }

    /**
     * Fetch credit types used in the district.
     * Returns ordered list of credit type names (e.g., "Contact Hours", "CEUs", "Graduate Credits").
     *
     * Includes both PUBLISHED and SUPERSEDED credit types to match main app behavior.
     * SUPERSEDED credit types are included because they may still be referenced in
     * historical attendance records and need to appear in reports.
     */
    private List<CreditType> fetchCreditTypes(Integer districtId) {
        log.debug("Fetching credit types for district {}", districtId);

        // Status values: 1=UNPUBLISHED, 2=PUBLISHED, 3=SUPERSEDED
        // Include PUBLISHED (2) and SUPERSEDED (3) to match main app behavior
        String sql = "SELECT pd_credit_type_id, name FROM pd_credit_types " +
                "WHERE district_id = ? AND status IN (2, 3) " +
                "ORDER BY order_id";

        try {
            List<CreditType> creditTypes = jdbcTemplate.query(sql,
                (rs, rowNum) -> new CreditType(
                    rs.getInt("pd_credit_type_id"),
                    rs.getString("name")
                ),
                districtId);
            log.debug("Found {} credit types for district {}", creditTypes.size(), districtId);
            return creditTypes;
        } catch (Exception e) {
            log.error("Error fetching credit types for district {}", districtId, e);
            // Return default credit type if query fails
            return List.of(new CreditType(1, "Contact Hours"));
        }
    }

    /**
     * Build dynamic column headers based on selected user/event properties and sources.
     */
    private List<String> buildColumnHeaders(ActivityByUserCriteria criteria, boolean multipleSourcesEnabled) {
        List<String> headers = new ArrayList<>();

        // Add user property columns (e.g., School, Job Title, Department)
        if (criteria.getUserProperties() != null) {
            headers.addAll(criteria.getUserProperties());
        }

        // Add source column if multiple sources are enabled
        if (multipleSourcesEnabled) {
            headers.add("Source");
        }

        // Add fixed columns
        headers.add("Program Name");

        // Add event property columns or default columns
        if (criteria.getEventProperties() != null && !criteria.getEventProperties().isEmpty()) {
            headers.addAll(criteria.getEventProperties());
        } else {
            headers.add("Title");
            headers.add("Date");
        }

        return headers;
    }

    /**
     * Query PD Tracking data source for activity records.
     * Main tables: pd_advanced_events, pd_slot_attendances, pd_event_attendances, pd_programs
     */
    private List<ActivityByUserReportData.ActivityRecord> queryPDTrackingSource(
            ActivityByUserCriteria criteria,
            List<CreditType> creditTypes) {

        log.debug("Querying PD Tracking source for {} users", criteria.getUserIds().size());

        List<ActivityByUserReportData.ActivityRecord> records = new ArrayList<>();

        // Query for each credit type separately (as done in original implementation)
        for (int i = 0; i < creditTypes.size(); i++) {
            final int creditTypeIndex = i; // Make effectively final for lambda
            CreditType creditType = creditTypes.get(creditTypeIndex);
            String creditTypeName = creditType.name;

            try {
                // Build the SQL query
                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();

                sql.append("SELECT ")
                        .append("u.user_id, ")
                        .append("CONCAT(u.first_name, ' ', u.last_name) as display_name, ")
                        .append("e.event_id, ")
                        .append("e.title as event_title, ")
                        .append("e.start_date as event_start, ")
                        .append("e.location as location, ")
                        .append("p.name as program_name, ")
                        .append("COALESCE(CASE WHEN e.user_credit_type = ? ")
                        .append("  THEN SUM(psa.value) ELSE pea.value END, 0) as credit_value ")
                        .append("FROM (");

                // Subquery to parse credit values from comma-delimited string
                sql.append("  SELECT psa.*, ? as credit_type_id, ")
                        .append("  SUBSTRING_INDEX(SUBSTRING_INDEX(psa.credit_values, ',', ")
                        .append("    FIND_IN_SET(?, psa.pd_credit_types)), ',', -1) as value ")
                        .append("  FROM pd_slot_attendances psa ")
                        .append("  WHERE district_id = ? ")
                        .append(") psa ");

                // UserCreditType: NONE=0, PER_EVENT=1, PER_SLOT=2
                params.add(2);  // UserCreditType.PER_SLOT = 2
                params.add(creditType.id);  // Use actual credit type ID (e.g., 102, 229, 356...)
                params.add(creditType.id);
                params.add(criteria.getDistrictId());

                // Join with other tables
                sql.append("JOIN pd_slots ON psa.slot_id = pd_slots.slot_id ")
                        .append("JOIN schedule_events ON schedule_events.schedule_event_id = pd_slots.schedule_event_id ")
                        .append("  AND schedule_events.repeat_state != 3 ")
                        .append("INNER JOIN pd_advanced_events e ON psa.event_id = e.event_id ")
                        .append("INNER JOIN users u ON psa.user_id = u.user_id ")
                        .append("INNER JOIN (")
                        .append("  SELECT pdea.*, ? as credit_type_id, ")
                        .append("  SUBSTRING_INDEX(SUBSTRING_INDEX(pdea.credit_values, ',', ")
                        .append("    FIND_IN_SET(?, pdea.pd_credit_types)), ',', -1) as value ")
                        .append("  FROM pd_event_attendances pdea ")
                        .append("  WHERE district_id = ? ")
                        .append(") pea ON psa.event_id = pea.pd_event_id AND psa.user_id = pea.user_id ")
                        .append("LEFT OUTER JOIN pd_programs p ON e.program_id = p.program_id ")
                        .append("LEFT OUTER JOIN pd_evidences pev ON pea.pd_event_attendance_id = pev.attendance_id ");

                params.add(creditType.id);  // Use actual credit type ID
                params.add(creditType.id);
                params.add(criteria.getDistrictId());

                // Event attribute filtering
                sql.append(buildEventAttributeFilter(criteria, params));

                // Main WHERE clause
                sql.append("e.district_id = ? ");
                params.add(criteria.getDistrictId());

                // User ID filter
                sql.append("AND u.user_id IN (");
                for (int userIdx = 0; userIdx < criteria.getUserIds().size(); userIdx++) {
                    sql.append(userIdx > 0 ? ",?" : "?");
                    params.add(criteria.getUserIds().get(userIdx));
                }
                sql.append(") ");

                // Attendance approval conditions
                sql.append("AND ((psa.signup_date IS NOT NULL AND psa.approval_date IS NOT NULL ")
                        .append("  AND ((NOT p.author_can_edit_options & ? AND p.credits_released_by_options = 0) ")
                        .append("    OR (p.author_can_edit_options & ? AND e.credits_released_by_type = 0) ")
                        .append("    OR psa.credits_release_date IS NOT NULL)) ")
                        .append("  OR e.program_id = 0) ");

                // Credit release flags (bitwise operations)
                int creditReleaseFlag = 1; // 1 << 0 for AuthorCanEditOption.CREDIT_RELEASED
                params.add(creditReleaseFlag);
                params.add(creditReleaseFlag);

                // Program and event status
                sql.append("AND ((p.state > ? AND e.status = ?) OR e.program_id = 0) ");
                params.add(0); // Program.State.ARCHIVED = 0
                params.add(5); // Event.Status.ACCEPTED = 5

                // Feedback requirement
                sql.append("AND (p.require_feedback != ? OR pea.feedback_completed OR e.program_id = 0) ");
                params.add(2); // REQUIRED = 2

                // Evidence requirement
                int evidenceFlag = 2; // 1 << 1 for AuthorCanEditOption.EVIDENCE
                sql.append("AND ((NOT p.author_can_edit_options & ? AND (p.require_evidence IS NULL OR p.require_evidence != ?)) ")
                        .append("  OR (p.author_can_edit_options & ? AND (e.require_evidence IS NULL OR e.require_evidence != ?)) ")
                        .append("  OR pev.state = ? OR e.program_id = 0) ");
                params.add(evidenceFlag);
                params.add(2); // REQUIRED = 2
                params.add(evidenceFlag);
                params.add(2); // REQUIRED = 2
                params.add(2); // APPROVED = 2

                // Date range filter
                sql.append(buildDateFilter(criteria, params));

                // Program filter
                if (criteria.getProgramId() != null && criteria.getProgramId() != 0) {
                    sql.append("AND e.program_id = ? ");
                    params.add(criteria.getProgramId());
                }

                // Group by
                sql.append("GROUP BY psa.user_id, e.event_id ")
                        .append("ORDER BY u.last_name, psa.signup_date");

                // Log query for debugging
                log.debug("Executing PD Tracking query for credit type {}: {} parameters",
                    creditTypes.get(creditTypeIndex), params.size());
                log.trace("SQL: {}", sql.toString());
                log.trace("Parameters: {}", params);

                // TEMPORARY: Test with simplified query to find blocking condition
                List<Object> simplifiedParams = new ArrayList<>();
                String simplifiedSql = buildSimplifiedQuery(criteria, creditTypeIndex, simplifiedParams);
                log.debug("SIMPLIFIED QUERY TEST - Checking record count with {} users in date range...",
                    Math.min(10, criteria.getUserIds().size()));
                try {
                    Integer simplifiedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM (" + simplifiedSql + ") tmp",
                        simplifiedParams.toArray(),
                        Integer.class
                    );
                    log.info("SIMPLIFIED QUERY (district={}, users={}, dateRange={} to {}) found {} records",
                        criteria.getDistrictId(),
                        Math.min(10, criteria.getUserIds().size()),
                        criteria.getStartDate(),
                        criteria.getEndDate(),
                        simplifiedCount != null ? simplifiedCount : 0);
                } catch (Exception e) {
                    log.error("SIMPLIFIED QUERY failed: {}", e.getMessage());
                }

                // Execute query
                jdbcTemplate.query(sql.toString(), params.toArray(), (rs) -> {
                    int userId = rs.getInt("user_id");
                    int eventId = rs.getInt("event_id");
                    String recordKey = eventId + "_" + userId + "_" + creditTypeIndex;

                    // Check if we already have this record (from a previous credit type query)
                    ActivityByUserReportData.ActivityRecord record = records.stream()
                            .filter(r -> r.getEventId() == eventId && r.getUserId() == userId)
                            .findFirst()
                            .orElse(null);

                    if (record == null) {
                        // Create new record
                        record = new ActivityByUserReportData.ActivityRecord();
                        record.setUserId(userId);
                        record.setUserName(rs.getString("display_name"));
                        record.setEventId(eventId);
                        record.setSource(SOURCE_PD_TRACKING);
                        record.setProgramName(rs.getString("program_name"));
                        record.setEventTitle(rs.getString("event_title"));

                        // Convert java.sql.Timestamp to LocalDateTime
                        java.sql.Timestamp timestamp = rs.getTimestamp("event_start");
                        if (timestamp != null) {
                            record.setEventDate(timestamp.toLocalDateTime());
                        }

                        // Initialize credit values array
                        List<Float> creditValues = new ArrayList<>();
                        for (int ctIdx = 0; ctIdx < creditTypes.size(); ctIdx++) {
                            creditValues.add(0.0f);
                        }
                        record.setCreditValues(creditValues);

                        // Build column values based on criteria
                        record.setColumnValues(buildColumnValues(criteria, record, rs));

                        records.add(record);
                    }

                    // Set credit value for this credit type
                    float creditValue = rs.getFloat("credit_value");
                    record.getCreditValues().set(creditTypeIndex, creditValue);

                    // Update total credits
                    float total = record.getCreditValues().stream()
                            .reduce(0.0f, Float::sum);
                    record.setTotalCredits(total);
                });

            } catch (Exception e) {
                log.error("Error querying PD Tracking source for credit type: {}", creditTypeName, e);
            }
        }

        log.debug("Retrieved {} PD Tracking records", records.size());
        return records;
    }

    /**
     * Query Vector Training data source for activity records.
     * Main tables: coursedata, courses, coursestatus, course_variants, course_variant_credits_new
     */
    private List<ActivityByUserReportData.ActivityRecord> queryVectorTrainingSource(
            ActivityByUserCriteria criteria,
            List<CreditType> creditTypes) {

        log.debug("Querying Vector Training source for {} users", criteria.getUserIds().size());

        List<ActivityByUserReportData.ActivityRecord> records = new ArrayList<>();

        // Track if we've found the default (PUBLISHED) credit type
        boolean foundDefault = false;

        for (int i = 0; i < creditTypes.size(); i++) {
            final int creditTypeIndex = i; // Make effectively final for lambda
            CreditType creditType = creditTypes.get(creditTypeIndex);
            String creditTypeName = creditType.name;

            // Determine if this is the default credit type (simplified - assumes first is default)
            boolean isDefault = !foundDefault && creditTypeIndex == 0;
            if (isDefault) {
                foundDefault = true;
            }

            try {
                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();

                // Credit calculation varies based on whether this is the default credit type
                String creditString = isDefault
                        ? "IF ((coursedata.complete_date IS NULL OR districtprefs.safeschools_show_course_results = 0), " +
                        "0, COALESCE(cvc.value, course_variants.duration / 60))"
                        : "IF ((coursedata.complete_date IS NULL OR districtprefs.safeschools_show_course_results = 0), " +
                        "0, COALESCE(cvc.value, 0))";

                sql.append("SELECT coursedata.coursedata_id, ")
                        .append("coursedata.user_id, ")
                        .append("CONCAT(u.first_name, ' ', u.last_name) as display_name, ")
                        .append("courses.course_id as event_id, ")
                        .append("coursedata.complete_date as event_start, ")
                        .append("courses.title as event_title, ")
                        .append(creditString).append(" as credit_value ")
                        .append("FROM coursedata ")
                        .append("JOIN users u ON coursedata.user_id = u.user_id ")
                        .append("JOIN userdata ON userdata.userdata_id = coursedata.userdata_id ")
                        .append("  AND userdata.district_id = coursedata.district_id ")
                        .append("JOIN courses ON courses.external_course_id = coursedata.course_id ")
                        .append("  AND courses.district_id = coursedata.district_id ")
                        .append("JOIN coursestatus ON coursestatus.coursedata_id = coursedata.coursedata_id ")
                        .append("  AND coursestatus.district_id = coursedata.district_id ")
                        .append("LEFT JOIN course_variants ON coursedata.course_variant_id = course_variants.external_course_variant_id ")
                        .append("  AND coursedata.district_id = course_variants.district_id ")
                        .append("JOIN districtprefs ON districtprefs.district_id = coursedata.district_id ")
                        .append("LEFT JOIN course_variant_credits_new cvc ON courses.course_id = cvc.course_id ")
                        .append("  AND course_variants.course_variant_id = cvc.course_variant_id ")
                        .append("  AND courses.district_id = cvc.district_id ")
                        .append("  AND cvc.credit_type_id = ? ");

                params.add(creditTypeIndex + 1);

                sql.append("WHERE coursedata.district_id = ? ")
                        .append("AND userdata.state > ? ")
                        .append("AND coursestatus.completed = 1 ");

                params.add(criteria.getDistrictId());
                params.add(1); // STATE_ARCHIVED = 1

                // Date filter for coursedata
                String dateFilter = buildDateFilter(criteria, params)
                        .replace("e.start_date", "coursedata.create_time")
                        .replace("e.end_date", "coursedata.complete_date");
                sql.append(dateFilter);

                // User ID filter
                sql.append("AND coursedata.user_id IN (");
                for (int userIdx = 0; userIdx < criteria.getUserIds().size(); userIdx++) {
                    sql.append(userIdx > 0 ? ",?" : "?");
                    params.add(criteria.getUserIds().get(userIdx));
                }
                sql.append(") ");

                sql.append("GROUP BY coursedata.course_id, u.user_id ")
                        .append("ORDER BY u.last_name, coursedata.complete_date");

                // Execute query
                jdbcTemplate.query(sql.toString(), params.toArray(), (rs) -> {
                    int userId = rs.getInt("user_id");
                    int eventId = rs.getInt("event_id");

                    // Check if we already have this record
                    ActivityByUserReportData.ActivityRecord record = records.stream()
                            .filter(r -> r.getEventId() == eventId && r.getUserId() == userId)
                            .findFirst()
                            .orElse(null);

                    if (record == null) {
                        record = new ActivityByUserReportData.ActivityRecord();
                        record.setUserId(userId);
                        record.setUserName(rs.getString("display_name"));
                        record.setEventId(eventId);
                        record.setSource(SOURCE_VECTOR_TRAINING);
                        record.setProgramName(""); // No program for Vector Training
                        record.setEventTitle(rs.getString("event_title"));

                        java.sql.Timestamp timestamp = rs.getTimestamp("event_start");
                        if (timestamp != null) {
                            record.setEventDate(timestamp.toLocalDateTime());
                        }

                        // Initialize credit values
                        List<Float> creditValues = new ArrayList<>();
                        for (int ctIdx = 0; ctIdx < creditTypes.size(); ctIdx++) {
                            creditValues.add(0.0f);
                        }
                        record.setCreditValues(creditValues);

                        record.setColumnValues(buildColumnValues(criteria, record, rs));
                        records.add(record);
                    }

                    // Set credit value
                    float creditValue = rs.getFloat("credit_value");
                    record.getCreditValues().set(creditTypeIndex, creditValue);

                    // Update total
                    float total = record.getCreditValues().stream().reduce(0.0f, Float::sum);
                    record.setTotalCredits(total);
                });

            } catch (Exception e) {
                log.error("Error querying Vector Training source for credit type: {}", creditTypeName, e);
            }
        }

        log.debug("Retrieved {} Vector Training records", records.size());
        return records;
    }

    /**
     * Query Canvas data source for activity records.
     * Main tables: canvas_course_progress, canvas_courses, canvas_course_credits_new
     */
    private List<ActivityByUserReportData.ActivityRecord> queryCanvasSource(
            ActivityByUserCriteria criteria,
            List<CreditType> creditTypes) {

        log.debug("Querying Canvas source for {} users", criteria.getUserIds().size());

        List<ActivityByUserReportData.ActivityRecord> records = new ArrayList<>();

        for (int i = 0; i < creditTypes.size(); i++) {
            final int creditTypeIndex = i; // Make effectively final for lambda
            CreditType creditType = creditTypes.get(creditTypeIndex);
            String creditTypeName = creditType.name;

            try {
                StringBuilder sql = new StringBuilder();
                List<Object> params = new ArrayList<>();

                sql.append("SELECT ccp.id as course_progress_id, ")
                        .append("u.user_id, ")
                        .append("CONCAT(u.first_name, ' ', u.last_name) as display_name, ")
                        .append("ccp.course_id as event_id, ")
                        .append("ccp.completed_at as event_start, ")
                        .append("cc.name as event_title, ")
                        .append("IF (ccp.completed_at IS NOT NULL AND ccp.requirement_completed_count >= ccp.requirement_count, ")
                        .append("  ccc.value, 0) as credit_value ")
                        .append("FROM canvas_course_progress ccp ")
                        .append("JOIN users u ON ccp.district_id = u.district_id ")
                        .append("  AND ccp.canvas_user_id = u.canvas_id ")
                        .append("JOIN canvas_courses cc ON ccp.course_id = cc.canvas_id ")
                        .append("  AND cc.district_id = ccp.district_id ")
                        .append("LEFT JOIN canvas_course_credits_new ccc ON cc.id = ccc.course_id ")
                        .append("  AND ccc.district_id = ccp.district_id ")
                        .append("  AND ccc.credit_type_id = ? ");

                params.add(creditTypeIndex + 1);

                sql.append("WHERE ccp.district_id = ? ");
                params.add(criteria.getDistrictId());

                // Date filter for Canvas (enrollment_created_at)
                if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
                    sql.append("AND (ccp.enrollment_created_at IS NULL ");
                    sql.append("OR (ccp.enrollment_created_at >= ? AND ccp.enrollment_created_at <= ?)) ");
                    params.add(java.sql.Date.valueOf(criteria.getStartDate()));
                    params.add(java.sql.Date.valueOf(criteria.getEndDate()));
                }

                // User ID filter
                sql.append("AND u.user_id IN (");
                for (int userIdx = 0; userIdx < criteria.getUserIds().size(); userIdx++) {
                    sql.append(userIdx > 0 ? ",?" : "?");
                    params.add(criteria.getUserIds().get(userIdx));
                }
                sql.append(") ");

                // Completion requirements
                sql.append("AND ccp.completed_at IS NOT NULL ")
                        .append("AND ccp.requirement_completed_count >= ccp.requirement_count ");

                sql.append("GROUP BY ccp.course_id, u.user_id ")
                        .append("ORDER BY u.last_name, ccp.completed_at");

                // Execute query
                jdbcTemplate.query(sql.toString(), params.toArray(), (rs) -> {
                    int userId = rs.getInt("user_id");
                    int eventId = rs.getInt("event_id");

                    // Check if we already have this record
                    ActivityByUserReportData.ActivityRecord record = records.stream()
                            .filter(r -> r.getEventId() == eventId && r.getUserId() == userId)
                            .findFirst()
                            .orElse(null);

                    if (record == null) {
                        record = new ActivityByUserReportData.ActivityRecord();
                        record.setUserId(userId);
                        record.setUserName(rs.getString("display_name"));
                        record.setEventId(eventId);
                        record.setSource(SOURCE_CANVAS);
                        record.setProgramName(""); // No program for Canvas
                        record.setEventTitle(rs.getString("event_title"));

                        java.sql.Timestamp timestamp = rs.getTimestamp("event_start");
                        if (timestamp != null) {
                            record.setEventDate(timestamp.toLocalDateTime());
                        }

                        // Initialize credit values
                        List<Float> creditValues = new ArrayList<>();
                        for (int ctIdx = 0; ctIdx < creditTypes.size(); ctIdx++) {
                            creditValues.add(0.0f);
                        }
                        record.setCreditValues(creditValues);

                        record.setColumnValues(buildColumnValues(criteria, record, rs));
                        records.add(record);
                    }

                    // Set credit value
                    float creditValue = rs.getFloat("credit_value");
                    record.getCreditValues().set(creditTypeIndex, creditValue);

                    // Update total
                    float total = record.getCreditValues().stream().reduce(0.0f, Float::sum);
                    record.setTotalCredits(total);
                });

            } catch (Exception e) {
                log.error("Error querying Canvas source for credit type: {}", creditTypeName, e);
            }
        }

        log.debug("Retrieved {} Canvas records", records.size());
        return records;
    }

    /**
     * Calculate total credits by credit type across all records.
     */
    private Map<String, Float> calculateCreditTotals(
            List<ActivityByUserReportData.ActivityRecord> records,
            List<CreditType> creditTypes) {

        Map<String, Float> totals = new LinkedHashMap<>();

        // Initialize totals for each credit type
        for (CreditType creditType : creditTypes) {
            totals.put(creditType.name, 0.0f);
        }

        // Sum up credits from all records
        for (ActivityByUserReportData.ActivityRecord record : records) {
            if (record.getCreditValues() != null) {
                for (int i = 0; i < record.getCreditValues().size() && i < creditTypes.size(); i++) {
                    String creditTypeName = creditTypes.get(i).name;
                    Float currentTotal = totals.get(creditTypeName);
                    Float recordValue = record.getCreditValues().get(i);
                    totals.put(creditTypeName, currentTotal + recordValue);
                }
            }
        }

        return totals;
    }

    /**
     * Build event attribute filter clause for PD Tracking queries.
     * Adds WHERE clause to filter events by selected event attributes.
     */
    private String buildEventAttributeFilter(ActivityByUserCriteria criteria, List<Object> params) {
        if (criteria.getEventAttributeFilters() == null || criteria.getEventAttributeFilters().isEmpty()) {
            return "WHERE ";
        }

        // Group filters by attribute ID
        Map<Integer, List<ActivityByUserCriteria.EventAttributeFilter>> attributeMap = new HashMap<>();
        for (ActivityByUserCriteria.EventAttributeFilter filter : criteria.getEventAttributeFilters()) {
            attributeMap.computeIfAbsent(filter.getEventAttributeId(), k -> new ArrayList<>()).add(filter);
        }

        StringBuilder filter = new StringBuilder(" WHERE e.event_id IN (");
        filter.append("  SELECT pae.event_id FROM pd_advanced_events pae")
                .append("  INNER JOIN pd_event_attribute_values peav ON pae.event_id = peav.event_id")
                .append("  INNER JOIN pd_event_attribute_options peao ON peav.event_attribute_option_id = peao.id")
                .append("  INNER JOIN pd_event_attributes peattr ON peattr.id = peao.event_attribute_id")
                .append("  WHERE ");

        boolean firstAttribute = true;
        for (Map.Entry<Integer, List<ActivityByUserCriteria.EventAttributeFilter>> entry : attributeMap.entrySet()) {
            if (!firstAttribute) {
                filter.append(" OR ");
            }
            filter.append("(peattr.id = ? AND peav.event_attribute_option_id IN (");
            params.add(entry.getKey());

            boolean firstOption = true;
            for (ActivityByUserCriteria.EventAttributeFilter attrFilter : entry.getValue()) {
                if (!firstOption) {
                    filter.append(",");
                }
                filter.append("?");
                params.add(attrFilter.getEventAttributeOptionId());
                firstOption = false;
            }
            filter.append("))");
            firstAttribute = false;
        }

        filter.append("  GROUP BY pae.event_id")
                .append("  HAVING COUNT(DISTINCT peattr.id) >= ?)") // All attribute filters must match
                .append(" AND ");

        params.add(attributeMap.size());

        return filter.toString();
    }

    /**
     * Build date range filter clause.
     * Filters events/courses by start and end dates.
     */
    private String buildDateFilter(ActivityByUserCriteria criteria, List<Object> params) {
        if (criteria.getStartDate() == null || criteria.getEndDate() == null) {
            return "";
        }

        StringBuilder filter = new StringBuilder();
        filter.append("AND ((e.start_date >= ? AND e.start_date <= ?) ")
                .append("OR (e.end_date >= ? AND e.end_date <= ?)) ");

        params.add(java.sql.Date.valueOf(criteria.getStartDate()));
        params.add(java.sql.Date.valueOf(criteria.getEndDate()));
        params.add(java.sql.Date.valueOf(criteria.getStartDate()));
        params.add(java.sql.Date.valueOf(criteria.getEndDate()));

        return filter.toString();
    }

    /**
     * TEMPORARY: Build simplified query for debugging to find which condition blocks data.
     * Tests with minimal conditions to verify base data exists.
     */
    private String buildSimplifiedQuery(ActivityByUserCriteria criteria, int creditTypeIndex, List<Object> params) {
        // params list is passed in empty, we just build it here

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT psa.user_id, e.event_id ")
                .append("FROM pd_slot_attendances psa ")
                .append("JOIN pd_slots ON psa.slot_id = pd_slots.slot_id ")
                .append("JOIN schedule_events ON schedule_events.schedule_event_id = pd_slots.schedule_event_id ")
                .append("  AND schedule_events.repeat_state != 3 ")
                .append("INNER JOIN pd_advanced_events e ON psa.event_id = e.event_id ")
                .append("INNER JOIN users u ON psa.user_id = u.user_id ")
                .append("WHERE e.district_id = ? ");

        params.add(criteria.getDistrictId());

        // Add user filter
        sql.append("AND u.user_id IN (");
        for (int i = 0; i < Math.min(10, criteria.getUserIds().size()); i++) {  // Test with first 10 users
            sql.append(i > 0 ? ",?" : "?");
            params.add(criteria.getUserIds().get(i));
        }
        sql.append(") ");

        // Add simple date filter
        if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
            sql.append("AND e.start_date >= ? AND e.start_date <= ? ");
            params.add(java.sql.Date.valueOf(criteria.getStartDate()));
            params.add(java.sql.Date.valueOf(criteria.getEndDate()));
        }

        sql.append("GROUP BY psa.user_id, e.event_id");

        return sql.toString();
    }

    /**
     * Build dynamic column values for a record based on selected user/event properties.
     * This creates the columnValues list that corresponds to the columnHeaders.
     *
     * IMPORTANT: The order of values MUST match the order of headers in buildColumnHeaders():
     * 1. User properties (e.g., FIRST_NAME, LAST_NAME, EMAIL, SCHOOL, JOB)
     * 2. Source (if multiple sources are enabled)
     * 3. Program Name
     * 4. Event properties (e.g., EVENT, EVENT_START) or defaults (Title, Date)
     *
     * Note: For properties that require database joins (SCHOOL, JOB, REGION, etc.),
     * these are queried separately. For optimal performance, consider adding these
     * columns to the main SQL queries in a future enhancement.
     */
    private List<String> buildColumnValues(
            ActivityByUserCriteria criteria,
            ActivityByUserReportData.ActivityRecord record,
            java.sql.ResultSet rs) {

        List<String> values = new ArrayList<>();

        try {
            // 1. Add user property values (if requested)
            if (criteria.getUserProperties() != null && !criteria.getUserProperties().isEmpty()) {
                values.addAll(extractUserPropertyValues(
                        criteria.getUserProperties(),
                        record.getUserId(),
                        criteria.getDistrictId(),
                        rs
                ));
            }

            // 2. Add source column (if multiple sources are enabled)
            Set<String> sources = criteria.getSources();
            boolean multipleSourcesEnabled = sources != null && sources.size() > 1;
            if (multipleSourcesEnabled) {
                values.add(record.getSource() != null ? record.getSource() : "");
            }

            // 3. Add program name
            values.add(record.getProgramName() != null ? record.getProgramName() : "");

            // 4. Add event property values (if requested) or defaults
            if (criteria.getEventProperties() != null && !criteria.getEventProperties().isEmpty()) {
                values.addAll(extractEventPropertyValues(
                        criteria.getEventProperties(),
                        record,
                        rs
                ));
            } else {
                // Add default columns: Title and Date
                values.add(record.getEventTitle() != null ? record.getEventTitle() : "");
                values.add(record.getEventDate() != null ?
                    record.getEventDate().toLocalDate().toString() : "");
            }

        } catch (Exception e) {
            log.error("Error building column values", e);
        }

        return values;
    }

    /**
     * Extract user property values for a user.
     * Basic properties (FIRST_NAME, LAST_NAME, etc.) are extracted from the ResultSet.
     * Properties requiring joins (SCHOOL, JOB, etc.) are queried separately.
     */
    private List<String> extractUserPropertyValues(
            List<String> propertyNames,
            Integer userId,
            Integer districtId,
            java.sql.ResultSet rs) {

        List<String> values = new ArrayList<>();

        for (String propertyName : propertyNames) {
            try {
                UserProperty property = UserProperty.fromString(propertyName);
                String value = "";

                switch (property) {
                    case FIRST_NAME:
                    case LAST_NAME:
                    case USERNAME:
                    case EMPLOYEE_ID:
                    case LOCAL_ID:
                    case EMAIL:
                    case CLIENT:
                    case LOCATION:
                    case HIRE_DATE:
                        // These can be extracted from the users table
                        value = getUserPropertyFromDatabase(userId, districtId, property);
                        break;

                    case SCHOOL:
                    case JOB:
                    case REGION:
                    case DEPARTMENT:
                    case PROFESSIONAL_STATUS:
                    case SUBJECT:
                    case SUPERVISOR:
                        // These require joins to lookup tables
                        value = getUserPropertyWithLookup(userId, districtId, property);
                        break;

                    default:
                        log.warn("Unknown user property: {}", propertyName);
                }

                values.add(value != null ? value : "");

            } catch (IllegalArgumentException e) {
                log.warn("Invalid user property name: {}", propertyName);
                values.add("");
            }
        }

        return values;
    }

    /**
     * Extract event property values from the record and ResultSet.
     */
    private List<String> extractEventPropertyValues(
            List<String> propertyNames,
            ActivityByUserReportData.ActivityRecord record,
            java.sql.ResultSet rs) {

        List<String> values = new ArrayList<>();

        for (String propertyName : propertyNames) {
            try {
                EventProperty property = EventProperty.fromString(propertyName);
                String value = "";

                switch (property) {
                    case EVENT:
                        value = record.getEventTitle();
                        break;

                    case EVENT_START:
                        value = record.getEventDate() != null ?
                                record.getEventDate().toString() : "";
                        break;

                    case SLOT_TITLE:
                    case EVENT_STATUS:
                    case LOCATION:
                    case PRESENTER:
                    case REIMBURSEMENT_CODE:
                    case REIMBURSEMENT_SUB_CODE:
                        // These would require additional SELECT columns in the main query
                        // For now, try to extract from ResultSet if available
                        value = getEventPropertyFromResultSet(rs, property);
                        break;

                    default:
                        log.warn("Unknown event property: {}", propertyName);
                }

                values.add(value != null ? value : "");

            } catch (IllegalArgumentException e) {
                log.warn("Invalid event property name: {}", propertyName);
                values.add("");
            }
        }

        return values;
    }

    /**
     * Get a basic user property value from the database.
     */
    private String getUserPropertyFromDatabase(Integer userId, Integer districtId, UserProperty property) {
        try {
            String sql = "SELECT " + property.getColumnName() +
                        " FROM users WHERE user_id = ? AND district_id = ?";

            return jdbcTemplate.queryForObject(sql, String.class, userId, districtId);

        } catch (Exception e) {
            log.debug("Could not fetch {} for user {}: {}", property.name(), userId, e.getMessage());
            return "";
        }
    }

    /**
     * Get a user property value that requires a join to a lookup table.
     */
    private String getUserPropertyWithLookup(Integer userId, Integer districtId, UserProperty property) {
        try {
            String sql;
            switch (property) {
                case SCHOOL:
                    sql = "SELECT s.name FROM users u " +
                         "JOIN schools s ON u.school_id = s.school_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                case JOB:
                    sql = "SELECT j.name FROM users u " +
                         "JOIN jobs j ON u.job_id = j.job_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                case REGION:
                    sql = "SELECT r.name FROM users u " +
                         "JOIN regions r ON u.region_id = r.region_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                case DEPARTMENT:
                    sql = "SELECT d.name FROM users u " +
                         "JOIN departments d ON u.department_id = d.department_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                case PROFESSIONAL_STATUS:
                    sql = "SELECT p.name FROM users u " +
                         "JOIN pstatus p ON u.profstatus_id = p.profstatus_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                case SUBJECT:
                    sql = "SELECT s.name FROM users u " +
                         "JOIN subjects s ON u.subject_id = s.subject_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                case SUPERVISOR:
                    sql = "SELECT CONCAT(s.first_name, ' ', s.last_name) FROM users u " +
                         "JOIN users s ON u.supervisor_id = s.user_id " +
                         "WHERE u.user_id = ? AND u.district_id = ?";
                    break;

                default:
                    return "";
            }

            return jdbcTemplate.queryForObject(sql, String.class, userId, districtId);

        } catch (Exception e) {
            log.debug("Could not fetch {} for user {}: {}", property.name(), userId, e.getMessage());
            return "";
        }
    }

    /**
     * Try to extract event property value from ResultSet if column exists.
     */
    private String getEventPropertyFromResultSet(java.sql.ResultSet rs, EventProperty property) {
        try {
            switch (property) {
                case SLOT_TITLE:
                    try {
                        return rs.getString("slot_name");
                    } catch (Exception e) {
                        return "";
                    }

                case LOCATION:
                    try {
                        return rs.getString("location");
                    } catch (Exception e) {
                        return "";
                    }

                case EVENT_STATUS:
                case PRESENTER:
                case REIMBURSEMENT_CODE:
                case REIMBURSEMENT_SUB_CODE:
                    // These aren't currently in the SELECT clause
                    // Would need to add them to the main SQL queries
                    return "";

                default:
                    return "";
            }

        } catch (Exception e) {
            log.debug("Could not extract {} from ResultSet: {}", property.name(), e.getMessage());
            return "";
        }
    }
}
