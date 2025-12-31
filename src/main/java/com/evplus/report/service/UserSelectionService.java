package com.evplus.report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

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
     * - MY_EVALUEES (-3): Reserved for future implementation with permission layer
     *
     * Note: Permission filtering will be added later through a dedicated permission service.
     *
     * @param districtId District ID for the report
     * @param requestedUserIds User IDs from the report criteria (may contain special values)
     * @return Resolved list of actual user IDs
     */
    public List<Integer> resolveUserIds(
            Integer districtId,
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
            // TODO: Implement MY_EVALUEES with proper permission layer
            logger.warn("MY_EVALUEES (-3) not yet implemented. Will be added with permission service.");
            return Collections.emptyList();
        }

        // Regular user IDs - return as-is
        // TODO: Add permission filtering through permission service
        return requestedUserIds;
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
}
