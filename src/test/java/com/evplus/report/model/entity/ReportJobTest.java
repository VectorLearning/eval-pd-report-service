package com.evplus.report.model.entity;

import com.evplus.report.model.enums.ReportStatus;
import com.evplus.report.model.enums.ReportType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReportJob entity.
 */
class ReportJobTest {

    @Test
    void testStatusEnumConversion() {
        ReportJob job = new ReportJob();

        // Test setting status via enum
        job.setStatus(ReportStatus.QUEUED);
        assertEquals(0, job.getStatusCode());
        assertEquals(ReportStatus.QUEUED, job.getStatus());

        job.setStatus(ReportStatus.PROCESSING);
        assertEquals(1, job.getStatusCode());
        assertEquals(ReportStatus.PROCESSING, job.getStatus());

        job.setStatus(ReportStatus.COMPLETED);
        assertEquals(2, job.getStatusCode());
        assertEquals(ReportStatus.COMPLETED, job.getStatus());

        job.setStatus(ReportStatus.FAILED);
        assertEquals(3, job.getStatusCode());
        assertEquals(ReportStatus.FAILED, job.getStatus());
    }

    @Test
    void testStatusCodeConversion() {
        ReportJob job = new ReportJob();

        // Test setting status via integer code
        job.setStatusCode(0);
        assertEquals(ReportStatus.QUEUED, job.getStatus());

        job.setStatusCode(1);
        assertEquals(ReportStatus.PROCESSING, job.getStatus());

        job.setStatusCode(2);
        assertEquals(ReportStatus.COMPLETED, job.getStatus());

        job.setStatusCode(3);
        assertEquals(ReportStatus.FAILED, job.getStatus());
    }

    @Test
    void testOnCreateCallback() {
        ReportJob job = new ReportJob();
        job.onCreate();

        assertNotNull(job.getCreatedAt());
        assertNotNull(job.getUpdatedAt());
        assertNotNull(job.getRequestedDate());
    }

    @Test
    void testOnUpdateCallback() throws InterruptedException {
        ReportJob job = new ReportJob();
        job.onCreate();
        LocalDateTime initialUpdatedAt = job.getUpdatedAt();

        Thread.sleep(10); // Small delay to ensure timestamp difference
        job.onUpdate();

        assertTrue(job.getUpdatedAt().isAfter(initialUpdatedAt));
    }

    @Test
    void testEntityConstruction() {
        String reportId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        ReportJob job = new ReportJob();
        job.setReportId(reportId);
        job.setDistrictId(123);
        job.setUserId(456);
        job.setReportType(ReportType.USER_ACTIVITY);
        job.setReportParams("{\"startDate\":\"2025-01-01\"}");
        job.setStatus(ReportStatus.QUEUED);
        job.setRequestedDate(now);

        assertEquals(reportId, job.getReportId());
        assertEquals(123, job.getDistrictId());
        assertEquals(456, job.getUserId());
        assertEquals(ReportType.USER_ACTIVITY, job.getReportType());
        assertEquals("{\"startDate\":\"2025-01-01\"}", job.getReportParams());
        assertEquals(ReportStatus.QUEUED, job.getStatus());
        assertEquals(now, job.getRequestedDate());
    }

    @Test
    void testNullStatusHandling() {
        ReportJob job = new ReportJob();
        job.setStatus(null);
        assertNull(job.getStatusCode());
        assertNull(job.getStatus());
    }
}
