package com.evplus.report.model.enums;

/**
 * Enumeration of standard user properties that can be included as columns
 * in the Activity By User report.
 *
 * These properties correspond to the original TeachPoint UserPropertyFilterField.Property enum.
 * Some properties require database joins to lookup tables (marked with requiresJoin()).
 */
public enum UserProperty {
    /**
     * User's first name (from users.first_name)
     */
    FIRST_NAME("first_name", "First Name", false),

    /**
     * User's last name (from users.last_name)
     */
    LAST_NAME("last_name", "Last Name", false),

    /**
     * Login username (from users.username)
     */
    USERNAME("username", "Username", false),

    /**
     * Employee identifier (from users.employee_id)
     */
    EMPLOYEE_ID("employee_id", "Employee ID", false),

    /**
     * Local identifier (from users.local_id)
     */
    LOCAL_ID("local_id", "Local ID", false),

    /**
     * Email address (from users.email)
     */
    EMAIL("email", "Email", false),

    /**
     * School name (requires join to schools table via users.school_id)
     */
    SCHOOL("school_id", "School", true),

    /**
     * Job title (requires join to jobs table via users.job_id)
     */
    JOB("job_id", "Job", true),

    /**
     * Region (requires join to regions table via users.region_id)
     */
    REGION("region_id", "Region", true),

    /**
     * Department (requires join to departments table via users.department_id)
     */
    DEPARTMENT("department_id", "Department", true),

    /**
     * Client (from users.client)
     */
    CLIENT("client", "Client", false),

    /**
     * Location (from users.location)
     */
    LOCATION("location", "Location", false),

    /**
     * Professional status (requires join to pstatus table via users.profstatus_id)
     */
    PROFESSIONAL_STATUS("profstatus_id", "Professional Status", true),

    /**
     * Subject area (requires join to subjects table via users.subject_id)
     */
    SUBJECT("subject_id", "Subject", true),

    /**
     * Hire date (from users.hire_date)
     */
    HIRE_DATE("hire_date", "Hire Date", false),

    /**
     * Supervisor name (requires lookup of supervisor user via users.supervisor_id)
     */
    SUPERVISOR("supervisor_id", "Supervisor", true);

    private final String columnName;
    private final String displayName;
    private final boolean requiresJoin;

    UserProperty(String columnName, String displayName, boolean requiresJoin) {
        this.columnName = columnName;
        this.displayName = displayName;
        this.requiresJoin = requiresJoin;
    }

    /**
     * Get the database column name for this property
     */
    public String getColumnName() {
        return columnName;
    }

    /**
     * Get the display name for this property (used in report headers)
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns true if this property requires a database join to a lookup table
     */
    public boolean requiresJoin() {
        return requiresJoin;
    }

    /**
     * Parse property name from string, case-insensitive
     */
    public static UserProperty fromString(String name) {
        for (UserProperty prop : values()) {
            if (prop.name().equalsIgnoreCase(name)) {
                return prop;
            }
        }
        throw new IllegalArgumentException("Invalid user property: " + name);
    }

    /**
     * Check if a string is a valid user property name
     */
    public static boolean isValid(String name) {
        try {
            fromString(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
