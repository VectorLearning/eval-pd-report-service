package com.evplus.report.service;

import com.evplus.report.model.dto.UserGroupFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for resolving user selections in reports.
 * Handles special user selection values like "All Users" and "My Evaluees".
 *
 * Special User Selection Constants:
 * - ALL_USERS_DISTRICT (-2): All active users in the district (filtered by permissions)
 * - MY_EVALUEES (-3): Users supervised/evaluated by the requester
 */
@Service
public class UserSelectionService {

    private static final Logger logger = LoggerFactory.getLogger(UserSelectionService.class);

    /**
     * Special value representing "All Users in District"
     */
    public static final int ALL_USERS_DISTRICT = -2;

    /**
     * Special value representing "My Evaluees"
     */
    public static final int MY_EVALUEES = -3;

    private final JdbcTemplate jdbcTemplate;

    public UserSelectionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolve user IDs from selection criteria.
     *
     * This method handles special selection values:
     * - ALL_USERS_DISTRICT (-2): Returns all active users in the district
     * - MY_EVALUEES (-3): Returns users that the requesting user has permission to view
     *
     * @param districtId District ID for the report
     * @param requestingUserId User ID of the person making the request (for MY_EVALUEES)
     * @param requestedUserIds User IDs from the report criteria (may contain special values)
     * @return Resolved list of actual user IDs
     */
    public List<Integer> resolveUserIds(
            Integer districtId,
            Integer requestingUserId,
            List<Integer> requestedUserIds) {

        if (requestedUserIds == null || requestedUserIds.isEmpty()) {
            logger.warn("Empty user ID list provided for district={}", districtId);
            return Collections.emptyList();
        }

        // Check for special values
        if (requestedUserIds.contains(ALL_USERS_DISTRICT)) {
            logger.info("Resolving ALL_USERS_DISTRICT for district={}", districtId);
            return getAllActiveUsers(districtId);
        }

        if (requestedUserIds.contains(MY_EVALUEES)) {
            logger.info("Resolving MY_EVALUEES for user={}, district={}", requestingUserId, districtId);
            return getMyEvaluees(districtId, requestingUserId);
        }

        // Regular user IDs - return as-is
        // TODO: Add permission filtering through permission service if needed
        return requestedUserIds;
    }

    /**
     * Resolve user IDs from selection criteria with user group filtering.
     *
     * This method first resolves special selection values, then applies user group filters.
     * Multiple filter groups are OR-ed together (union of matching users).
     *
     * @param districtId District ID for the report
     * @param requestingUserId User ID of the person making the request (for MY_EVALUEES)
     * @param requestedUserIds User IDs from the report criteria (may contain special values)
     * @param userGroupFilters Optional list of user group filters (schools, jobs, regions, etc.)
     * @return Resolved and filtered list of actual user IDs
     */
    public List<Integer> resolveUserIdsWithFilter(
            Integer districtId,
            Integer requestingUserId,
            List<Integer> requestedUserIds,
            List<UserGroupFilter> userGroupFilters) {

        // Step 1: Resolve special user selections (-2, -3) to actual user IDs
        List<Integer> resolvedUserIds = resolveUserIds(districtId, requestingUserId, requestedUserIds);

        if (resolvedUserIds.isEmpty()) {
            return resolvedUserIds;
        }

        // Step 2: Apply user group filters if provided
        if (userGroupFilters != null && !userGroupFilters.isEmpty()) {
            // Remove empty filters
            List<UserGroupFilter> nonEmptyFilters = userGroupFilters.stream()
                    .filter(filter -> filter != null && !filter.isEmpty())
                    .collect(Collectors.toList());

            if (!nonEmptyFilters.isEmpty()) {
                logger.info("Applying {} user group filter(s) to {} users",
                        nonEmptyFilters.size(), resolvedUserIds.size());
                resolvedUserIds = applyMultipleUserGroupFilters(districtId, resolvedUserIds, nonEmptyFilters);
                logger.info("After filtering: {} users remain", resolvedUserIds.size());
            }
        }

        return resolvedUserIds;
    }

    /**
     * Get all active users in the district.
     * Excludes only SUPER_ROOT users (type = 0).
     */
    private List<Integer> getAllActiveUsers(Integer districtId) {
        String sql = "SELECT user_id FROM users " +
                     "WHERE district_id = ? " +
                     "AND state > 0 " +  // STATE_NORMAL = 1 (active users)
                     "AND type != 0 " +  // Exclude TYPE_SUPER_ROOT (0) only
                     "ORDER BY last_name, first_name";

        List<Integer> userIds = jdbcTemplate.queryForList(sql, Integer.class, districtId);
        logger.info("Found {} active users in district {}", userIds.size(), districtId);
        return userIds;
    }

    /**
     * Get evaluees (users with PD viewing permission) for a requesting user.
     *
     * This queries the permissions table to find all users that the requesting user
     * has TYPE_PD_VIEW_USER (3006) permission for. Permissions can be:
     * - District-wide (district = 1): All users in the district
     * - Filtered by school, job, subject, region, department, team, or grade
     * - Direct user-to-user (target_id): Specific individual users
     *
     * All filter conditions are AND-ed together per permission row.
     * Multiple permission rows are OR-ed (union of all matching users).
     *
     * @param districtId District ID
     * @param requestingUserId User ID making the request
     * @return List of user IDs that the requesting user can view
     */
    private List<Integer> getMyEvaluees(Integer districtId, Integer requestingUserId) {
        String sql =
            "SELECT DISTINCT u.user_id " +
            "FROM users u " +
            "WHERE u.district_id = ? " +
            "  AND u.state >= 1 " +  // Active users only (NORMAL or SUSPENDED)
            "  AND u.type != 0 " +    // Exclude SUPER_ROOT users
            "  AND EXISTS (" +
            "    SELECT 1 " +
            "    FROM permissions p " +
            "    WHERE p.user_id = ? " +
            "      AND p.district_id = ? " +
            "      AND p.type = 3006 " +  // TYPE_PD_VIEW_USER
            "      AND (" +
            "        p.district = 1 " +  // District-wide permission
            "        OR (" +
            "          (p.school_id = 0 OR p.school_id = u.school_id) " +
            "          AND (p.job_id = 0 OR p.job_id = u.job_id) " +
            "          AND (p.subject_id = 0 OR p.subject_id = u.subject_id) " +
            "          AND (p.region_id = 0 OR p.region_id = u.region_id) " +
            "          AND (p.department_id = 0 OR p.department_id = u.department_id) " +
            "          AND (p.target_id = 0 OR p.target_id = u.user_id) " +
            // For group_id, need to check if user is in the group via usergroups table
            "          AND (p.group_id = 0 OR EXISTS (" +
            "            SELECT 1 FROM usergroups ug " +
            "            WHERE ug.user_id = u.user_id " +
            "              AND ug.group_id = p.group_id" +
            "          ))" +
            "        )" +
            "      )" +
            "  ) " +
            "ORDER BY u.last_name, u.first_name";

        List<Integer> evalueeIds = jdbcTemplate.queryForList(
            sql,
            Integer.class,
            districtId,           // WHERE u.district_id = ?
            requestingUserId,     // WHERE p.user_id = ?
            districtId            // AND p.district_id = ?
        );

        logger.info("Found {} evaluees for user={} in district={}",
                    evalueeIds.size(), requestingUserId, districtId);
        return evalueeIds;
    }

    /**
     * Check if a user ID list contains any special selection values.
     */
    public boolean containsSpecialSelection(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }
        return userIds.contains(ALL_USERS_DISTRICT) ||
               userIds.contains(MY_EVALUEES);
    }

    /**
     * Get a description of the user selection for logging/display purposes.
     */
    public String getSelectionDescription(List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return "No users selected";
        }

        if (userIds.contains(ALL_USERS_DISTRICT)) {
            return "All users in district";
        }

        if (userIds.contains(MY_EVALUEES)) {
            return "My evaluees";
        }

        return userIds.size() + " specific user(s)";
    }

    /**
     * Apply multiple user group filters with OR logic.
     *
     * Each filter group is applied independently, and results are combined (union).
     * - Within each filter: All criteria are AND-ed
     * - Between filters: Results are OR-ed (union)
     *
     * Example: Filter1 (School 1 AND Job 5) OR Filter2 (Region 10 AND Job 6)
     *
     * @param districtId District ID
     * @param userIds List of user IDs to filter
     * @param userGroupFilters List of filter criteria
     * @return Filtered list of user IDs (union of all matching users)
     */
    private List<Integer> applyMultipleUserGroupFilters(
            Integer districtId,
            List<Integer> userIds,
            List<UserGroupFilter> userGroupFilters) {

        if (userIds == null || userIds.isEmpty() || userGroupFilters == null || userGroupFilters.isEmpty()) {
            return userIds;
        }

        // If only one filter, use direct method
        if (userGroupFilters.size() == 1) {
            return applyUserGroupFilter(districtId, userIds, userGroupFilters.get(0));
        }

        // Apply each filter and collect results in a Set (for union/OR logic)
        Set<Integer> matchedUserIds = new HashSet<>();

        for (int i = 0; i < userGroupFilters.size(); i++) {
            UserGroupFilter filter = userGroupFilters.get(i);
            logger.debug("Applying filter group {} of {}", i + 1, userGroupFilters.size());

            List<Integer> filteredIds = applyUserGroupFilter(districtId, userIds, filter);
            matchedUserIds.addAll(filteredIds);

            logger.debug("Filter group {} matched {} users", i + 1, filteredIds.size());
        }

        // Convert Set back to List and sort by user ID for consistency
        List<Integer> result = new ArrayList<>(matchedUserIds);
        Collections.sort(result);

        logger.debug("Total unique users matched across all filters: {}", result.size());
        return result;
    }

    /**
     * Apply a single user group filter to a list of user IDs.
     *
     * Filters users based on organizational properties like schools, jobs, regions, etc.
     * All filters within this group are AND-ed together (user must match all specified criteria).
     *
     * @param districtId District ID
     * @param userIds List of user IDs to filter
     * @param userGroupFilter Filter criteria (schools, jobs, etc.)
     * @return Filtered list of user IDs
     */
    private List<Integer> applyUserGroupFilter(
            Integer districtId,
            List<Integer> userIds,
            UserGroupFilter userGroupFilter) {

        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Build dynamic SQL query with filters
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT DISTINCT u.user_id FROM users u ");

        // Add join for team/group filtering if needed
        if (userGroupFilter.hasTeamFilter()) {
            sql.append("LEFT JOIN usergroups ug ON u.user_id = ug.user_id ");
        }

        sql.append("WHERE u.district_id = ? ");
        params.add(districtId);

        // Filter by the provided user ID list
        sql.append("AND u.user_id IN (");
        sql.append(userIds.stream().map(id -> "?").collect(Collectors.joining(",")));
        sql.append(") ");
        params.addAll(userIds);

        // Add user group filters
        Map<String, List<Integer>> filterMap = userGroupFilter.toFilterMap();
        for (Map.Entry<String, List<Integer>> entry : filterMap.entrySet()) {
            String columnName = entry.getKey();
            List<Integer> values = entry.getValue();

            if (values != null && !values.isEmpty()) {
                sql.append("AND u.").append(columnName).append(" IN (");
                sql.append(values.stream().map(v -> "?").collect(Collectors.joining(",")));
                sql.append(") ");
                params.addAll(values);
            }
        }

        // Add team/group filter if present
        if (userGroupFilter.hasTeamFilter()) {
            sql.append("AND ug.group_id IN (");
            sql.append(userGroupFilter.getTeamIds().stream()
                    .map(v -> "?").collect(Collectors.joining(",")));
            sql.append(") ");
            params.addAll(userGroupFilter.getTeamIds());
        }

        sql.append("ORDER BY u.last_name, u.first_name");

        // Execute query
        List<Integer> filteredUserIds = jdbcTemplate.queryForList(
                sql.toString(),
                Integer.class,
                params.toArray()
        );

        logger.debug("User group filter reduced {} users to {} users",
                userIds.size(), filteredUserIds.size());

        return filteredUserIds;
    }
}
