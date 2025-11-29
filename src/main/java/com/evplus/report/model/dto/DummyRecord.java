package com.evplus.report.model.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Individual record in a DUMMY_TEST report.
 * Contains sample fields for testing data serialization, Excel generation, and S3 upload.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DummyRecord {

    /**
     * Sample field 1 - simulates a text field like user name.
     */
    private String field1;

    /**
     * Sample field 2 - simulates a text field like activity type.
     */
    private String field2;

    /**
     * Sample field 3 - simulates a text field like district name.
     */
    private String field3;

    /**
     * Sample timestamp field - simulates activity date/time.
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
}
