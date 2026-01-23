package com.evplus.report.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Filter criteria for user groups (Schools, Jobs, Regions, etc.).
 * Used to filter users based on their demographic and organizational properties.
 *
 * All filter conditions within a single filter are AND-ed together.
 * Multiple UserGroupFilter objects can be OR-ed together for complex queries.
 *
 * Example:
 * - Filter for users in School 1 OR School 2 AND Job 5:
 *   schoolIds=[1,2], jobIds=[5]
 *
 * Supported filter fields map to users table columns:
 * - schoolIds → users.school_id
 * - jobIds → users.job_id
 * - subjectIds → users.subject_id
 * - regionIds → users.region_id
 * - departmentIds → users.department_id
 * - professionalStatusIds → users.profstatus_id
 * - clientIds → users.client_id
 * - locationIds → users.location_id
 * - teamIds → usergroups.group_id (requires join with usergroups table)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupFilter {

    /**
     * School IDs filter.
     * Filters by users.school_id.
     */
    private List<Integer> schoolIds;

    /**
     * Job IDs filter.
     * Filters by users.job_id.
     */
    private List<Integer> jobIds;

    /**
     * Subject IDs filter.
     * Filters by users.subject_id.
     */
    private List<Integer> subjectIds;

    /**
     * Region IDs filter.
     * Filters by users.region_id.
     */
    private List<Integer> regionIds;

    /**
     * Department IDs filter.
     * Filters by users.department_id.
     */
    private List<Integer> departmentIds;

    /**
     * Professional Status IDs filter.
     * Filters by users.profstatus_id.
     */
    private List<Integer> professionalStatusIds;

    /**
     * Client IDs filter.
     * Filters by users.client_id.
     */
    private List<Integer> clientIds;

    /**
     * Location IDs filter.
     * Filters by users.location_id.
     */
    private List<Integer> locationIds;

    /**
     * Team/Group IDs filter.
     * Filters by usergroups.group_id (requires join with usergroups table).
     */
    private List<Integer> teamIds;

    /**
     * Check if this filter has any criteria set.
     *
     * @return true if at least one filter is non-empty, false otherwise
     */
    public boolean isEmpty() {
        return (schoolIds == null || schoolIds.isEmpty()) &&
               (jobIds == null || jobIds.isEmpty()) &&
               (subjectIds == null || subjectIds.isEmpty()) &&
               (regionIds == null || regionIds.isEmpty()) &&
               (departmentIds == null || departmentIds.isEmpty()) &&
               (professionalStatusIds == null || professionalStatusIds.isEmpty()) &&
               (clientIds == null || clientIds.isEmpty()) &&
               (locationIds == null || locationIds.isEmpty()) &&
               (teamIds == null || teamIds.isEmpty());
    }

    /**
     * Get all non-empty filter criteria as a map.
     * Used for building dynamic SQL queries.
     *
     * @return Map of column name to list of IDs
     */
    public Map<String, List<Integer>> toFilterMap() {
        Map<String, List<Integer>> filterMap = new HashMap<>();

        if (schoolIds != null && !schoolIds.isEmpty()) {
            filterMap.put("school_id", schoolIds);
        }
        if (jobIds != null && !jobIds.isEmpty()) {
            filterMap.put("job_id", jobIds);
        }
        if (subjectIds != null && !subjectIds.isEmpty()) {
            filterMap.put("subject_id", subjectIds);
        }
        if (regionIds != null && !regionIds.isEmpty()) {
            filterMap.put("region_id", regionIds);
        }
        if (departmentIds != null && !departmentIds.isEmpty()) {
            filterMap.put("department_id", departmentIds);
        }
        if (professionalStatusIds != null && !professionalStatusIds.isEmpty()) {
            filterMap.put("profstatus_id", professionalStatusIds);
        }
        if (clientIds != null && !clientIds.isEmpty()) {
            filterMap.put("client_id", clientIds);
        }
        if (locationIds != null && !locationIds.isEmpty()) {
            filterMap.put("location_id", locationIds);
        }

        return filterMap;
    }

    /**
     * Check if team filtering is required.
     * Team filtering requires a join with the usergroups table.
     *
     * @return true if teamIds is non-empty
     */
    public boolean hasTeamFilter() {
        return teamIds != null && !teamIds.isEmpty();
    }
}
