package com.evplus.report.model.dto;

import com.evplus.report.model.enums.ReportType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * Abstract base class for all report criteria.
 * Uses Jackson polymorphic deserialization to handle different report types.
 *
 * Each concrete report criteria class should:
 * 1. Extend this class
 * 2. Be registered in @JsonSubTypes annotation
 * 3. Implement getReportType() to return its specific type
 */
@Data
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "reportType"
)
@JsonSubTypes({
    // Concrete criteria classes will be registered here as they're created
    // @JsonSubTypes.Type(value = UserActivityCriteria.class, name = "USER_ACTIVITY"),
    // @JsonSubTypes.Type(value = DummyReportCriteria.class, name = "DUMMY_TEST")
})
public abstract class ReportCriteria {

    /**
     * Get the report type this criteria is for.
     * Must be implemented by concrete classes.
     *
     * @return the ReportType enum value
     */
    public abstract ReportType getReportType();
}
