package com.evplus.report.model.enums;

/**
 * Enumeration of standard event properties that can be included as columns
 * in the Activity By User report.
 *
 * These properties correspond to the original TeachPoint EventPropertyFilterField.Property enum.
 * Values are extracted from event and slot data.
 */
public enum EventProperty {
    /**
     * Event title (from pd_advanced_events.title)
     */
    EVENT("title", "Event"),

    /**
     * Time slot title (from pd_slots.name)
     */
    SLOT_TITLE("slot_name", "Slot Title"),

    /**
     * Event status (from pd_advanced_events.status)
     */
    EVENT_STATUS("status", "Event Status"),

    /**
     * Event location (from pd_advanced_events.location)
     */
    LOCATION("location", "Location"),

    /**
     * Presenter names (from slot_members, comma-separated for multiple presenters)
     */
    PRESENTER("presenters", "Presenter"),

    /**
     * Event start date/time (from schedule_events.start_date)
     */
    EVENT_START("start_date", "Event Start"),

    /**
     * Reimbursement code (from pd_advanced_events.reimbursement_code)
     */
    REIMBURSEMENT_CODE("reimbursement_code", "Reimbursement Code"),

    /**
     * Reimbursement sub-code (from pd_advanced_events.reimbursement_sub_code)
     */
    REIMBURSEMENT_SUB_CODE("reimbursement_sub_code", "Reimbursement Sub-Code");

    private final String columnName;
    private final String displayName;

    EventProperty(String columnName, String displayName) {
        this.columnName = columnName;
        this.displayName = displayName;
    }

    /**
     * Get the database column/field name for this property
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
     * Parse property name from string, case-insensitive
     */
    public static EventProperty fromString(String name) {
        for (EventProperty prop : values()) {
            if (prop.name().equalsIgnoreCase(name)) {
                return prop;
            }
        }
        throw new IllegalArgumentException("Invalid event property: " + name);
    }

    /**
     * Check if a string is a valid event property name
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
